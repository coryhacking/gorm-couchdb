/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.grails.plugins.couchdb

import grails.validation.ValidationException
import org.apache.commons.lang.StringUtils
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty
import org.codehaus.groovy.grails.plugins.DomainClassPluginSupport
import org.codehaus.groovy.grails.plugins.couchdb.domain.CouchDomainClass
import org.codehaus.groovy.grails.plugins.couchdb.domain.CouchDomainClassArtefactHandler
import org.codehaus.groovy.grails.plugins.couchdb.json.CouchDomainTypeMapper
import org.codehaus.groovy.grails.plugins.couchdb.json.JsonConverterUtils
import org.codehaus.groovy.grails.plugins.couchdb.json.JsonDateConverter
import org.codehaus.groovy.grails.plugins.couchdb.util.GrailsCouchDBUpdater
import org.codehaus.groovy.grails.support.SoftThreadLocalMap
import org.codehaus.groovy.grails.validation.GrailsDomainClassValidator
import org.codehaus.groovy.grails.web.binding.DataBindingLazyMetaPropertyMap
import org.codehaus.groovy.grails.web.binding.DataBindingUtils
import org.jcouchdb.db.Database
import org.jcouchdb.db.Options
import org.jcouchdb.document.Attachment
import org.jcouchdb.document.DesignDocument
import org.jcouchdb.document.ValueRow
import org.jcouchdb.exception.NotFoundException
import org.springframework.beans.BeanUtils
import org.springframework.beans.factory.config.MethodInvokingFactoryBean
import org.springframework.context.ApplicationContext
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.Errors
import org.svenson.JSON
import org.svenson.JSONConfig
import org.svenson.JSONParser
import org.svenson.converter.DefaultTypeConverterRepository
import org.jcouchdb.document.ValueAndDocumentRow

/**
 *
 * @author Warner Onstine, Cory Hacking
 */
public class CouchDBPluginSupport {

	private static final Log log = LogFactory.getLog(CouchDBPluginSupport.class)

	private static final String ARGUMENT_VALIDATE = "validate"
	private static final String ARGUMENT_FAIL_ON_ERROR = "failOnError"
	private static final String FAIL_ON_ERROR_CONFIG_PROPERTY = "grails.gorm.failOnError"

	static final PROPERTY_INSTANCE_MAP = new SoftThreadLocalMap()

	static def doWithSpring = {ApplicationContext ctx ->

		// extend the jcouchdb ValueRow class to automatically look up properties of the internal value object
		ValueRow.metaClass.propertyMissing = {String name ->

			// only look if the value is a Map; which should happen unless the view
			// emit doesn't contain a named parameters, e.g. emit(doc._id, 1)
			if (delegate.value instanceof Map) {
				Map map = delegate.value

				if (!map.containsKey(name)) {
					if (delegate instanceof ValueAndDocumentRow) {
						return delegate.document."${name}"
					}

					throw new MissingPropertyException(name)
				}

				// look for a property of the same name in our domain class
				Object value = map.get(name)
				if (value) {
					Class type = map.get('__domainClass')?.getPropertyByName(name)?.type
					if (type && !(type instanceof String) && !value.getClass().isAssignableFrom(type)) {
						value = JsonConverterUtils.fromJSON(type, value)
						map.put(name, value)
					}
				}

				return value

			} else {
				if (delegate instanceof ValueAndDocumentRow) {
					return delegate.document."${name}"
				}

				throw new MissingPropertyException(name)
			}
		}

		// register our CouchDomainClass artefacts that weren't already picked up by grails
		application.domainClasses.each {GrailsDomainClass dc ->
			if (CouchDomainClassArtefactHandler.isCouchDomainClass(dc.clazz)) {
				CouchDomainClass couchDomainClass = new CouchDomainClass(dc.clazz)
				application.addArtefact(CouchDomainClassArtefactHandler.TYPE, couchDomainClass)
			}
		}

		application.CouchDomainClasses.each {CouchDomainClass dc ->

			// Note the use of Groovy's ability to use dynamic strings in method names!
			"${dc.fullName}"(dc.clazz) { bean ->
				bean.singleton = false
				bean.autowire = "byName"
			}
			"${dc.fullName}DomainClass"(MethodInvokingFactoryBean) { bean ->
				targetObject = ref("grailsApplication", true)
				targetMethod = "getArtefact"
				bean.lazyInit = true
				arguments = [CouchDomainClassArtefactHandler.TYPE, dc.fullName]
			}
			"${dc.fullName}PersistentClass"(MethodInvokingFactoryBean) { bean ->
				targetObject = ref("${dc.fullName}DomainClass")
				bean.lazyInit = true
				targetMethod = "getClazz"
			}
			"${dc.fullName}Validator"(GrailsDomainClassValidator) { bean ->
				messageSource = ref("messageSource")
				bean.lazyInit = true
				domainClass = ref("${dc.fullName}DomainClass")
				grailsApplication = ref("grailsApplication", true)
			}
		}
	}

	static def doWithDynamicMethods = {ApplicationContext ctx ->
		enhanceDomainClasses(application, ctx)
	}

	static enhanceDomainClasses(GrailsApplication application, ApplicationContext ctx) {

		application.CouchDomainClasses.each {CouchDomainClass domainClass ->
			Database db = getCouchDatabase(application, domainClass, true)

			addInstanceMethods(application, domainClass, ctx, db)
			addStaticMethods(application, domainClass, ctx, db)
			addDynamicFinderSupport(application, domainClass, ctx, db)

			addValidationMethods(application, domainClass, ctx)

			addPropertiesSupport(application, domainClass, ctx)
		}

		// update the related document view(s)
		updateCouchViews(application)
	}

	static updateCouchViews(GrailsApplication application) {

		// the base path...
		def viewsPath = ((application.warDeployed) ? application.parentContext.servletContext.getRealPath("/WEB-INF") + "/grails-app/couchdb/views/" : "./grails-app/conf/couchdb/views/")
		def views = new File(viewsPath)

		// make sure the views directory exists...
		if (!views.exists() || !views.isDirectory()) {
			log.warn "The couchdb views directory [${viewsPath}] does not exist.  Views were not updated."
			return
		}

		// update the appropriate view for each domain class
		application.CouchDomainClasses.each {CouchDomainClass domainClass ->

			def view = new File(views, domainClass.designName)
			if (view.exists() && view.isDirectory()) {

				// Note that by design any map / reduce functions that are in couchdb but NOT here get
				// removed when updating.
				GrailsCouchDBUpdater updater = new GrailsCouchDBUpdater()
				updater.setDatabase(getCouchDatabase(application, domainClass, false))
				updater.setCreateDatabase(false)
				updater.setDesignDocumentDir(views)
				updater.setDesignName(domainClass.designName)
				updater.updateDesignDocuments()

			}
		}
	}

	private static addInstanceMethods(GrailsApplication application, CouchDomainClass dc, ApplicationContext ctx, Database db) {
		MetaClass metaClass = dc.metaClass
		CouchDomainClass domainClass = dc
		Database couchdb = db

		metaClass.save = {Map args = [:] ->

			boolean valid = (shouldValidate(args, domainClass)) ? validate() : true
			if (!valid) {

				boolean shouldFail = dc.shouldFailOnError
				if (args != null && args.containsKey(ARGUMENT_FAIL_ON_ERROR)) {
					shouldFail = GrailsClassUtils.getBooleanFromMap(ARGUMENT_FAIL_ON_ERROR, args)
				}
				if (shouldFail) {
					throw new ValidationException("Validation Error(s) occurred during save()", delegate.errors)
				}
				return null
			}

			couchdb.createOrUpdateDocument autoTimeStamp(application, delegate)

			return delegate
		}

		metaClass.delete = {->
			delete(null)
		}

		metaClass.delete = {Map args = [:] ->
			couchdb.delete getDocumentId(domainClass, delegate), getDocumentVersion(domainClass, delegate)
		}

		metaClass.readAttachment = {Serializable attachmentId ->
			return couchdb.getAttachment(getDocumentId(domainClass, delegate), attachmentId.toString())
		}

		metaClass.saveAttachment = {Serializable attachmentId, String contentType, byte[] data ->
			couchdb.createAttachment(getDocumentId(domainClass, delegate), getDocumentVersion(domainClass, delegate), attachmentId.toString(), contentType, data)
			return domainClass.getClazz().get(getDocumentId(domainClass, delegate))
		}

		metaClass.saveAttachment = {Serializable attachmentId, String contentType, InputStream is, long length ->
			couchdb.createAttachment(getDocumentId(domainClass, delegate), getDocumentVersion(domainClass, delegate), attachmentId.toString(), contentType, is, length)
			return domainClass.getClazz().get(getDocumentId(domainClass, delegate))
		}

		metaClass.deleteAttachment = {Serializable attachmentId ->
			couchdb.deleteAttachment(getDocumentId(domainClass, delegate), getDocumentVersion(domainClass, delegate), attachmentId.toString())
			return domainClass.getClazz().get(getDocumentId(domainClass, delegate))
		}

		metaClass.toJSON = {->
			return db.jsonConfig.getJsonGenerator().forValue(delegate)
		}
	}

	private static addStaticMethods(GrailsApplication application, CouchDomainClass dc, ApplicationContext ctx, Database db) {
		MetaClass metaClass = dc.metaClass
		CouchDomainClass domainClass = dc
		Database couchdb = db

		JSONParser readParser = null
		JSONParser queryParser = null

		// if we have subclasses, then create a special parser that has the appropriate
		// subclass type mappings for get and query results
		readParser = new JSONParser(db.getJsonConfig().jsonParser)
		queryParser = new JSONParser(db.getJsonConfig().jsonParser)

		if (domainClass.hasSubClasses()) {
			setDocTypeMapper domainClass, readParser
			setQueryDocTypeMapper domainClass, queryParser
		}

		metaClass.static.get = {Serializable docId ->
			try {
				return couchdb.getDocument(domainClass.clazz, docId.toString(), null, readParser)

			} catch (NotFoundException e) {
				// fall through to return null
			}

			return null
		}

		// Foo.exists(1)
		metaClass.static.exists = {Serializable docId ->
			get(docId) != null
		}

		metaClass.static.delete = {Serializable docId, String version ->
			couchdb.delete docId.toString(), version
		}

		metaClass.static.bulkSave = {List documents ->
			return bulkSave(documents, false)
		}

		metaClass.static.bulkSave = {List documents, Boolean allOrNothing ->
			documents.each {doc ->
				autoTimeStamp(application, doc)
			}

			return couchdb.bulkCreateDocuments(documents, allOrNothing)
		}

		metaClass.static.bulkDelete = {List documents ->
			return bulkDelete(documents, false)
		}

		metaClass.static.bulkDelete = {List documents, boolean allOrNothing ->
			return couchdb.bulkDeleteDocuments(documents, allOrNothing)
		}

		metaClass.static.readAttachment = {Serializable docId, String attachmentId ->
			return couchdb.getAttachment(docId.toString(), attachmentId)
		}

		metaClass.static.saveAttachment = {Serializable docId, String version, String attachmentId, String contentType, byte[] data ->
			return couchdb.createAttachment(docId.toString(), version, attachmentId, contentType, data)
		}

		metaClass.static.saveAttachment = {Serializable docId, String version, String attachmentId, String contentType, InputStream is, long length ->
			return couchdb.createAttachment(docId.toString(), version, attachmentId, contentType, is, length)
		}

		metaClass.static.deleteAttachment = {Serializable docId, String version, String attachmentId ->
			return couchdb.deleteAttachment(docId.toString(), version, attachmentId)
		}

		metaClass.static.findAll = {Map o = [:] ->
			return couchdb.listDocuments(getOptions(o), null).getRows()
		}

		metaClass.static.queryView = {String viewName, Map o = [:] ->
			def view = viewName
			if (!view.contains("/")) {
				view = domainClass.designName + "/" + view
			}

			def result
			if (isDocumentQuery(o)) {
				result = couchdb.queryViewAndDocuments(view, Map.class, domainClass.clazz, getOptions(o), queryParser)
			} else {
				result = couchdb.queryView(view, Map.class, getOptions(o), null)
			}

			result.getRows().each {row ->
				if (row.value instanceof Map) {
					row.value?.put('__domainClass', dc)
				}
			}

			return result.getRows()
		}

		metaClass.static.queryViewByKeys = {String viewName, List keys, Map o = [:] ->
			def view = viewName
			if (!view.contains("/")) {
				view = domainClass.designName + "/" + view
			}

			def result
			if (isDocumentQuery(o)) {
				result = couchdb.queryViewAndDocumentsByKeys(view, Map.class, domainClass.clazz, convertKeys(keys), getOptions(o), queryParser)
			} else {
				result = couchdb.queryViewByKeys(view, Map.class, convertKeys(keys), getOptions(o), null)
			}

			result.getRows().each {row ->
				if (row.value instanceof Map) {
					row.value?.put('__domainClass', dc)
				}
			}

			return result.getRows()
		}

		metaClass.static.getDesignDocument = {String id ->
			try {
				def view = id
				if (!view) {
					view = domainClass.designName
				}
				return couchdb.getDesignDocument(view)
			} catch (NotFoundException e) {
				// fall through to return null
			}

			return null
		}

		metaClass.static.saveDesignDocument = {DesignDocument doc ->
			if (!doc.id) {
				doc.id = domainClass.designName
			}

			return couchdb.createOrUpdateDocument(doc)
		}

		metaClass.static.deleteDesignDocument = {DesignDocument doc ->
			return couchdb.delete(doc)
		}

		metaClass.static.parse = {json ->
			return readParser.parse(dc.clazz, json as String)
		}

		metaClass.static.getCouchdb = {
			return couchdb
		}

		metaClass.static.getParser = {
			return readParser
		}

		metaClass.static.getQueryParser = {
			return queryParser
		}
	}

	private static addDynamicFinderSupport(GrailsApplication application, CouchDomainClass dc, ApplicationContext ctx, Database db) {
		def metaClass = dc.metaClass
		def domainClass = dc
		def couchdb = db

		// This adds basic dynamic finder support.
		metaClass.static.methodMissing = {String methodName, args ->

			// find methods (can have search keys)
			def matcher = (methodName =~ /^(find)((\w+)?)$/)
			if (!matcher.matches()) {

				// list methods (can have search keys)
				matcher = (methodName =~ /^(list)((\w+)?)$/)
				matcher.reset()
				if (!matcher.matches()) {

					// count methods (only options)
					matcher = (methodName =~ /^(count)((\w+)?)$/)
					matcher.reset()
					if (!matcher.matches()) {
						throw new MissingMethodException(methodName, delegate, args, true)
					}
				}
			}

			// set the view to everything after the method type (change first char to lowerCase).
			def method = matcher.group(1)
			def view = matcher.group(2) ?: method
			view = domainClass.designName + "/" + view.substring(0, 1).toLowerCase() + view.substring(1)

			// named arguments are placed first
			args = args.toList()
			def options = (args.size() > 0 && args[0] instanceof Map) ? args.remove(0) : [:]

			// call the appropriate query and return the results
			if (method == "find" || method == "list") {

				// assume that the list of keys (if any) is everything else
				def keys = (args ?: [])
				if (keys) {
					return queryViewByKeys(view, keys, options)
				} else {
					return queryView(view, options)
				}
			} else {
				def count = couchdb.queryView(view, Map.class, getOptions(options), null).getRows()
				return (count ? count[0].value : 0) as Long
			}
		}
	}

	private static addValidationMethods(GrailsApplication application, CouchDomainClass dc, ApplicationContext ctx) {
		MetaClass metaClass = dc.metaClass
		CouchDomainClass domainClass = dc

		registerConstraintsProperty(metaClass, domainClass)

		metaClass.hasErrors = {-> delegate.errors?.hasErrors() }

		def get
		def put
		try {
			def rch = application.classLoader.loadClass("org.springframework.web.context.request.RequestContextHolder")
			get = {
				def attributes = rch.getRequestAttributes()
				if (attributes) {
					return attributes.request.getAttribute(it)
				}
				return PROPERTY_INSTANCE_MAP.get().get(it)
			}
			put = { key, val ->
				def attributes = rch.getRequestAttributes()
				if (attributes) {
					attributes.request.setAttribute(key, val)
				} else {
					PROPERTY_INSTANCE_MAP.get().put(key, val)
				}
			}
		} catch (Throwable e) {
			get = { PROPERTY_INSTANCE_MAP.get().get(it) }
			put = { key, val -> PROPERTY_INSTANCE_MAP.get().put(key, val) }
		}

		metaClass.getErrors = {->
			def errors
			def key = "org.codehaus.groovy.grails.ERRORS_${delegate.class.name}_${System.identityHashCode(delegate)}"
			errors = get(key)
			if (!errors) {
				errors = new BeanPropertyBindingResult(delegate, delegate.getClass().getName())
				put key, errors
			}
			errors
		}
		metaClass.setErrors = { Errors errors ->
			def key = "org.codehaus.groovy.grails.ERRORS_${delegate.class.name}_${System.identityHashCode(delegate)}"
			put key, errors
		}
		metaClass.clearErrors = {->
			delegate.setErrors(new BeanPropertyBindingResult(delegate, delegate.getClass().getName()))
		}
		if (!domainClass.hasMetaMethod("validate")) {
			metaClass.validate = {->

				// clear the errors list before performing the validation
				clearErrors()

				// validate this instance
				DomainClassPluginSupport.validateInstance(delegate, ctx)
			}
		}
	}

	private static addPropertiesSupport(GrailsApplication application, CouchDomainClass dc, ApplicationContext ctx) {
		MetaClass metaClass = dc.metaClass
		CouchDomainClass domainClass = dc

		metaClass.constructor = { Map map ->
			def instance = ctx.containsBean(domainClass.fullName) ? ctx.getBean(domainClass.fullName) : BeanUtils.instantiateClass(domainClass.clazz)
			DataBindingUtils.bindObjectToDomainInstance(domainClass, instance, map)
			DataBindingUtils.assignBidirectionalAssociations(instance, map, domainClass)
			return instance
		}

		metaClass.setProperties = {Object o ->
			DataBindingUtils.bindObjectToDomainInstance(domainClass, delegate, o)
		}

		metaClass.getProperties = {->
			new DataBindingLazyMetaPropertyMap(delegate)
		}
	}

	static void registerConstraintsProperty(MetaClass metaClass, CouchDomainClass domainClass) {
		metaClass.'static'.getConstraints = {-> domainClass.constrainedProperties }

		metaClass.getConstraints = {-> domainClass.constrainedProperties }
	}

	private static Object autoTimeStamp(GrailsApplication application, Object domain) {

		CouchDomainClass dc = (CouchDomainClass) application.getArtefact(CouchDomainClassArtefactHandler.TYPE, domain.getClass().getName())
		if (dc) {
			def metaClass = dc.metaClass

			MetaProperty property = metaClass.hasProperty(dc, GrailsDomainClassProperty.DATE_CREATED)
			def time = System.currentTimeMillis()
			if (property && domain[property.name] == null && getDocumentVersion(dc, domain) == null) {
				def now = property.getType().newInstance([time] as Object[])
				domain[property.name] = now
			}

			property = metaClass.hasProperty(dc, GrailsDomainClassProperty.LAST_UPDATED)
			if (property) {
				def now = property.getType().newInstance([time] as Object[])
				domain[property.name] = now
			}
		}

		return domain
	}

	private static String getDocumentId(CouchDomainClass dc, Object domain) {
		def id = dc.getIdentifier()
		if (id) {
			domain[id.name]
		}
	}

	private static String getDocumentVersion(CouchDomainClass dc, Object domain) {
		def version = dc.getVersion()
		if (version) {
			domain[version.name]
		}
	}

	private static void setDocumentVersion(CouchDomainClass dc, Object domain, String newVersion) {
		def version = dc.getVersion()
		if (version) {
			domain[version.name] = newVersion
		}
	}

	private static Map<String, Attachment> getDocumentAttachments(CouchDomainClass dc, Object domain) {
		def att = dc.getAttachments()
		if (att) {
			domain[att.name]
		}
	}

	private static Database getCouchDatabase(GrailsApplication application, CouchDomainClass domainClass, boolean createDatabase) {
		def ds = application.config.couchdb
		def dbId = domainClass.databaseId

		String host = ds?.host ?: "localhost"
		Integer port = (ds?.port ?: 5984) as Integer
		String database = ds?.database ?: (dbId ?: application.metadata["app.name"])
		String username = ds?.username ?: ""
		String password = ds?.password ?: ""

		String realm = ds?.realm ?: null
		String scheme = ds?.scheme ?: null

		// get the datasource configuration for this specific db (if any)
		if (dbId && ds[dbId]) {
			ds = ds[dbId]

			host = ds.host ?: host
			port = ds.port ?: port
			database = ds.database ?: database
			username = ds.username ?: username
			password = ds.password ?: password
			realm = ds.realm ?: realm
			scheme = ds.scheme ?: scheme
		}

		Database db = new Database(host, port, database)

		// check to see if there are any user credentials and set them
		if (StringUtils.isNotEmpty(username)) {
			def credentials = new UsernamePasswordCredentials(username, password)
			def authScope = new AuthScope(host, port)

			// set the realm and scheme if they are set
			if (StringUtils.isNotEmpty(realm) || StringUtils.isNotEmpty(scheme)) {
				authScope = new AuthScope(host, port, realm, scheme)
			}

			db.server.setCredentials(authScope, credentials)
		}

		if (createDatabase) {
			if (db.getServer().createDatabase(db.getName())) {
				log.info("Database [${db.getName()}] created.")
			}
		}

		DefaultTypeConverterRepository typeConverterRepository = new DefaultTypeConverterRepository()
		JsonDateConverter dateConverter = new JsonDateConverter()
		typeConverterRepository.addTypeConverter(dateConverter)

		JSON generator = new JSON()
		generator.setIgnoredProperties(Arrays.asList("metaClass"))
		generator.setTypeConverterRepository(typeConverterRepository)
		generator.registerTypeConversion(java.util.Date.class, dateConverter)
		generator.registerTypeConversion(java.sql.Date.class, dateConverter)
		generator.registerTypeConversion(java.sql.Timestamp.class, dateConverter)

		JSONParser parser = new JSONParser()
		parser.setTypeConverterRepository(typeConverterRepository)
		parser.registerTypeConversion(java.util.Date.class, dateConverter)
		parser.registerTypeConversion(java.sql.Date.class, dateConverter)
		parser.registerTypeConversion(java.sql.Timestamp.class, dateConverter)

		db.jsonConfig = new JSONConfig(generator, parser)

		return db
	}

	private static void setDocTypeMapper(CouchDomainClass domainClass, JSONParser parser) {
		CouchDomainTypeMapper mapper = new CouchDomainTypeMapper()

		mapper.setParsePathInfo ""

		domainClass.getSubClassTypes().each {type, dc ->
			mapper.addFieldValueMapping(type, dc.clazz)
		}

		parser.setTypeMapper mapper
	}

	private static void setQueryDocTypeMapper(CouchDomainClass domainClass, JSONParser parser) {
		CouchDomainTypeMapper mapper = new CouchDomainTypeMapper()

		mapper.setParsePathInfo ".rows[].doc"

		domainClass.getSubClassTypes().each {type, dc ->
			mapper.addFieldValueMapping(type, dc.clazz)
		}

		parser.setTypeMapper mapper
	}

	private static Options getOptions(Map o) {
		def options = new Options()

		if (o) {
			o.each {String key, Object value ->
				switch (key) {
					case "key":
					case "startkey":
					case "startkey_docid":
					case "endkey":
					case "endkey_docid":
						// keys need to be encoded
						options.put(key, JsonConverterUtils.toJSON(value))
						break

					case "max":
					case "limit":
						options.put("limit", value)
						break

					case "offset":
					case "skip":
						options.put("skip", value)
						break

					case "order":
						options.descending((value == "desc"))
						break

					case "update":
					case "group":
					case "stale":
					case "reduce":
					case "include_docs":
						options.put(key, value)
						break

					default:
						// ignore everything else
						break
				}
			}
		}

		return options
	}

	private static boolean isDocumentQuery(Map o) {

		if (o['include_docs']) {
			return true
		} else {
			return false
		}
	}

	private static List convertKeys(List keys) {
		def values = []
		keys.each {key ->
			values << JsonConverterUtils.toJSON(key)
		}

		return values
	}

	/**
	 * Checks whether validation should be performed
	 * @return True if the domain class should be validated
	 * @param arguments The arguments to the validate method
	 * @param domainClass The domain class
	 */
	private static boolean shouldValidate(Map args, CouchDomainClass domainClass) {
		if (domainClass == null) {
			return false
		}

		if (args.length == 0) {
			return true
		}

		if (args.containsKey(ARGUMENT_VALIDATE)) {
			return GrailsClassUtils.getBooleanFromMap(ARGUMENT_VALIDATE, args)
		}

		return true
	}
}