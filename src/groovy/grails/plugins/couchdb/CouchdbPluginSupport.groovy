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
package grails.plugins.couchdb

import grails.plugins.couchdb.domain.CouchDomainClass
import grails.plugins.couchdb.domain.CouchDomainClassArtefactHandler
import net.sf.json.JSON
import net.sf.json.JSONObject
import net.sf.json.JSONSerializer
import net.sf.json.JsonConfig
import org.apache.commons.lang.StringUtils
import org.apache.commons.lang.time.DateFormatUtils
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty
import org.codehaus.groovy.grails.plugins.DomainClassPluginSupport
import org.codehaus.groovy.grails.support.SoftThreadLocalMap
import org.codehaus.groovy.grails.validation.GrailsDomainClassValidator
import org.codehaus.groovy.grails.web.binding.DataBindingLazyMetaPropertyMap
import org.codehaus.groovy.grails.web.binding.DataBindingUtils
import org.jcouchdb.db.Database
import org.jcouchdb.db.Options
import org.jcouchdb.document.Attachment
import org.jcouchdb.document.DesignDocument
import org.jcouchdb.document.ValueRow
import org.jcouchdb.document.ViewResult
import org.jcouchdb.exception.NotFoundException
import org.jcouchdb.util.CouchDBUpdater
import org.springframework.beans.BeanUtils
import org.springframework.beans.factory.config.MethodInvokingFactoryBean
import org.springframework.context.ApplicationContext
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.Errors

/**
 *
 * @author Warner Onstine, Cory Hacking
 */
public class CouchdbPluginSupport {

    static final PROPERTY_INSTANCE_MAP = new SoftThreadLocalMap()

    static def doWithSpring = {ApplicationContext ctx ->

        updateCouchViews(application)

        // extend the jcouchdb ValueRow class to automatically look up properties of the internal value object
        ValueRow.metaClass.propertyMissing = {String name ->
            Map map = delegate.value

            if (map == null || !map.containsKey(name)) {
                throw new MissingPropertyException(name)
            }

            // look for a property of the same name in our domain class
            Object value = map.get(name)
            Class type = map.get('_domainClass')?.getPropertyByName(name)?.type
            if (value != null && type != null && !(type instanceof String) && !value.class.isAssignableFrom(type)) {
                value = CouchJsonConfig.morph(type, value)
                map.put(name, value)
            }
            return value
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
            "${dc.fullName}"(dc.getClazz()) {bean ->
                bean.singleton = false
                bean.autowire = "byName"
            }

            "${dc.fullName}DomainClass"(MethodInvokingFactoryBean) {
                targetObject = ref("grailsApplication", true)
                targetMethod = "getArtefact"
                arguments = [CouchDomainClassArtefactHandler.TYPE, dc.fullName]
            }

            "${dc.fullName}PersistentClass"(MethodInvokingFactoryBean) {
                targetObject = ref("${dc.fullName}DomainClass")
                targetMethod = "getClazz"
            }

            "${dc.fullName}Validator"(GrailsDomainClassValidator) {
                messageSource = ref("messageSource")
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
            Database db = getCouchDatabase(application)

            addInstanceMethods(application, domainClass, ctx, db)
            addStaticMethods(application, domainClass, ctx, db)
            addDynamicFinderSupport(application, domainClass, ctx, db)

            addValidationMethods(application, domainClass, ctx)

        }
    }

    static updateCouchViews(GrailsApplication application) {
        def views

        // update the couch views
        if (application.warDeployed) {
            views = new File(application.parentContext.servletContext.getRealPath("/WEB-INF") + "/grails-app/couchdb/views" as String)
        } else {
            views = new File("./grails-app/conf/couchdb/views")
        }

        if (views.exists() && views.isDirectory()) {

            // Note that any map / reduce functions that are in couchdb but NOT here get
            // removed when updating.  I believe this is by design in jcouchdb.
            CouchDBUpdater updater = new CouchDBUpdater();
            updater.setDatabase(getCouchDatabase(application));
            updater.setDesignDocumentDir(views);
            updater.updateDesignDocuments();
        } else {
            println "Warning, the couchdb views directory [${views.name}, ${views.path}] does not exist.  Views were not updated."
        }
    }

    private static addInstanceMethods(GrailsApplication application, CouchDomainClass dc, ApplicationContext ctx, Database db) {
        def metaClass = dc.metaClass
        def domainClass = dc
        def couchdb = db

        metaClass.save = {->
            save(null)
        }

        metaClass.save = {Map args = [:] ->

            // todo: add support for failOnError:true in grails 1.2 (GRAILS-4343)
            if (validate()) {

                // create our couch document that can be serialized to json properly
                CouchDocument doc = convertToCouchDocument(application, autoTimeStamp(application, delegate))

                // save the document
                couchdb.createOrUpdateDocument doc

                // set the return id and revision on the domain object
                def id = domainClass.getIdentifier()
                if (id) {
                    delegate[id.name] = doc.id
                }

                def rev = domainClass.getVersion()
                if (rev) {
                    delegate[rev.name] = doc.revision
                }

                return delegate
            }

            return null
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
            return couchdb.createAttachment(getDocumentId(domainClass, delegate), getDocumentVersion(domainClass, delegate), attachmentId.toString(), contentType, data)
        }

        metaClass.saveAttachment = {Serializable attachmentId, String contentType, InputStream is, long length ->
            return couchdb.createAttachment(getDocumentId(domainClass, delegate), getDocumentVersion(domainClass, delegate), attachmentId.toString(), contentType, is, length)
        }

        metaClass.deleteAttachment = {Serializable attachmentId ->
            return couchdb.deleteAttachment(getDocumentId(domainClass, delegate), getDocumentVersion(domainClass, delegate), attachmentId.toString())
        }
    }

    private static addStaticMethods(GrailsApplication application, CouchDomainClass dc, ApplicationContext ctx, Database db) {
        def metaClass = dc.metaClass
        def domainClass = dc
        def couchdb = db

        metaClass.static.get = {Serializable docId ->
            try {
                // read the json document from the database
                def json = couchdb.getDocument(JSONObject.class, docId.toString())

                // convert it to our domain object
                // todo: make sure that the type matches the appropriate domain class or look it up
                return convertToDomainObject(domainClass, json)

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
            def couchDocuments = []

            documents.each {doc ->
                couchDocuments << convertToCouchDocument(application, autoTimeStamp(application, doc))
            }

            return couchdb.bulkCreateDocuments(couchDocuments, allOrNothing)
        }

        metaClass.static.bulkDelete = {List documents ->
            return bulkDelete(documents, false)
        }

        metaClass.static.bulkDelete = {List documents, boolean allOrNothing ->
            def couchDocuments = new ArrayList()

            documents.each {doc ->
                couchDocuments << convertToCouchDocument(application, doc)

            }

            return couchdb.bulkDeleteDocuments(couchDocuments, allOrNothing)
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

        metaClass.static.count = {Map o = [:] ->
            return count(null, o)
        }

        metaClass.static.count = {String viewName, Map o = [:] ->
            def view = viewName
            if (!view) {
                view = "count"
            }
            if (!view.contains("/")) {
                view = domainClass.designName + "/" + view
            }

            def count = couchdb.queryView(view, Map.class, getOptions(o), null).getRows()

            return (count ? count[0].value : 0) as Long
        }

        metaClass.static.list = {Map o = [:] ->
            return list(null, o)
        }

        metaClass.static.list = {String viewName, Map o = [:] ->
            def view = viewName
            if (!view) {
                view = "list"
            }

            return queryView(view, o)
        }

        metaClass.static.queryView = {String viewName, Map o = [:] ->
            def view = viewName
            if (!view.contains("/")) {
                view = domainClass.designName + "/" + view
            }
            ViewResult result = couchdb.queryView(view, Map.class, getOptions(o), null)
            result.getRows().each {row -> row.value?.put('_domainClass', dc)}

            return result.getRows()
        }

        metaClass.static.queryViewByKeys = {String viewName, List keys, Map o = [:] ->
            def view = viewName
            if (!view.contains("/")) {
                view = domainClass.designName + "/" + view
            }

            ViewResult result = couchdb.queryViewByKeys(view, Map.class, convertKeys(keys), getOptions(o), null)
            result.getRows().each {row -> row.value?.put('_domainClass', dc)}

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
    }

    private static addDynamicFinderSupport(GrailsApplication application, CouchDomainClass dc, ApplicationContext ctx, Database db) {
        def metaClass = dc.metaClass
        def domainClass = dc
        def couchdb = db

        // This adds basic dynamic finder support.
        metaClass.static.methodMissing = {String methodName, args ->

            // find methods (can have search keys)
            def matcher = (methodName =~ /^(find)(\w+)$/)
            if (!matcher.matches()) {

                // list methods (just contains options)
                matcher = (methodName =~ /^(list)(\w+)$/)
                matcher.reset()
                if (!matcher.matches()) {

                    // count methods (only options)
                    matcher = (methodName =~ /^(count)(\w+)$/)
                    matcher.reset()
                    if (!matcher.matches()) {
                        throw new MissingMethodException(methodName, delegate, args, true)
                    }
                }
            }

            // set the view to everything after the method type (change first char to lowerCase).
            def method = matcher.group(1)
            def view = matcher.group(2)
            view = domainClass.designName + "/" + view.substring(0, 1).toLowerCase() + view.substring(1)

            // options should be the last map argument.
            args = args.toList()
            def options = (args.size() > 0 && args[args.size() - 1] instanceof Map) ? args.remove(args.size() - 1) : [:]

            // call the appropriate query and return the results
            if (method == "find") {

                // assume that the list of keys (if any) is everything else
                def keys = (args ?: [])
                if (keys) {
                    return queryViewByKeys(view, keys, options);
                } else {
                    return queryView(view, options)
                }

            } else if (method == "list") {
                return queryView(view, options)

            } else {
                return count(view, options)

            }
        }
    }

    private static addValidationMethods(GrailsApplication application, CouchDomainClass dc, ApplicationContext ctx) {
        def metaClass = dc.metaClass
        def domainClass = dc

        metaClass.static.getConstraints = {->
            domainClass.constrainedProperties
        }

        metaClass.getConstraints = {->
            domainClass.constrainedProperties
        }

        metaClass.constructor = {Map map ->
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

        metaClass.hasErrors = {-> delegate.errors?.hasErrors() }

        def get
        def put
        try {
            def rch = application.classLoader.loadClass("org.springframework.web.context.request.RequestContextHolder")
            get = {
                def attributes = rch.getRequestAttributes()
                if (attributes) {
                    return attributes.request.getAttribute(it)
                } else {
                    return PROPERTY_INSTANCE_MAP.get().get(it)
                }
            }
            put = {key, val ->
                def attributes = rch.getRequestAttributes()
                if (attributes) {
                    attributes.request.setAttribute(key, val)
                } else {
                    PROPERTY_INSTANCE_MAP.get().put(key, val)
                }
            }
        } catch (Throwable e) {
            get = { PROPERTY_INSTANCE_MAP.get().get(it) }
            put = {key, val -> PROPERTY_INSTANCE_MAP.get().put(key, val) }
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

        metaClass.setErrors = {Errors errors ->
            def key = "org.codehaus.groovy.grails.ERRORS_${delegate.class.name}_${System.identityHashCode(delegate)}"
            put key, errors
        }

        metaClass.clearErrors = {->
            delegate.setErrors(new BeanPropertyBindingResult(delegate, delegate.getClass().getName()))
        }

        if (!metaClass.respondsTo(dc.getReference(), "validate")) {
            metaClass.validate = {->
                DomainClassPluginSupport.validateInstance(delegate, ctx)
            }
        }
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

    private static Object convertToDomainObject(CouchDomainClass dc, JSON json) {
        JsonConfig jsonConfig = new CouchJsonConfig();
        jsonConfig.setRootClass(dc.clazz);

        def id = json.remove("_id")
        def version = json.remove("_rev")
        def attachments = json.remove("_attachments")

        if (dc.type) {
            json.remove(dc.typeFieldName)
        }

        def doc = JSONSerializer.toJava(json, jsonConfig);

        def prop = dc.getIdentifier()
        if (prop) {
            doc[prop.name] = id
        }

        prop = dc.getVersion()
        if (prop) {
            doc[prop.name] = version
        }

        prop = dc.getAttachments()
        if (prop) {
            def converted = [:]
            if (attachments) {

                // Convert the attachments one at a time because json doesn't know what
                // the type should be.  I'm sure there's another way to do this, but I really
                // just want to use the standard jcouchdb / svenson libs so we'll wait for
                // that work instead.
                attachments.each {String key, value ->
                    Attachment att = new Attachment()

                    att.stub = true
                    att.contentType = value["content_type"]
                    att.length = value['length']
                    att.revPos = value['revpos']

                    converted.put(key, att)
                }
            }

            doc[prop.name] = converted
        }

        return doc
    }

    private static CouchDocument convertToCouchDocument(GrailsApplication application, Object domain) {

        // would be nice to just use the standard jcouchdb/svenson libs but they don't
        // deal with groovy objects cleanly yet...
        CouchDocument doc = new CouchDocument()

        CouchDomainClass dc = (CouchDomainClass) application.getArtefact(CouchDomainClassArtefactHandler.TYPE, domain.getClass().getName())
        if (dc) {

            // set the document type if it is enabled set it first, so that it can be
            //  overridden by an actual type property if there is one...
            if (dc.type) {
                doc[dc.typeFieldName] = dc.type
            }

            // set all of the persistant properties.
            dc.getPersistantProperties().each() {prop ->
                doc[prop.name] = domain[prop.name]
            }

            // set the document id, revision, and attachments... do it last so that the
            //  annotated properties override any other ones that may be there.  Especially
            //  needed if hibernate is already enabled as id and version fields are created,
            //  but may not be used.
            doc.id = getDocumentId(dc, domain)
            doc.revision = getDocumentVersion(dc, domain)
            doc.attachments = getDocumentAttachments(dc, domain)

        } else {

            // todo: what do we do with an object we don't know how to convert?

        }

        return doc
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

    private static Map<String, Attachment> getDocumentAttachments(CouchDomainClass dc, Object domain) {
        def att = dc.getAttachments()
        if (att) {
            domain[att.name]
        }
    }

    private static Database getCouchDatabase(GrailsApplication application) {
        def ds = application.config.couchdb

        String host = ds?.host ?: "localhost"
        Integer port = ds?.port ?: 5984
        String database = ds?.database ?: application.metadata["app.name"]
        String username = ds?.username ?: ""
        String password = ds?.password ?: ""

        String realm = ds?.realm ?: null
        String scheme = ds?.scheme ?: null

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

        return db
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
                        options.put(key, convertKey(value))
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

    private static List convertKeys(List keys) {
        def values = []
        keys.each {key ->
            values << convertKey(key)
        }

        return values
    }

    private static Object convertKey(Object key) {
        def value = key

        if (value instanceof java.sql.Date) {
            value = new Date(((java.sql.Date) value).getTime());
        }

        if (value instanceof Date) {
            value = DateFormatUtils.formatUTC((Date) key, CouchJsonDateValueProcessor.DATE_PATTERN)
        }

        return value
    }
}