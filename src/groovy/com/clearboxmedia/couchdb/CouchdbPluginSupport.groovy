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
package com.clearboxmedia.couchdb

import com.clearboxmedia.couchdb.domain.CouchDomainClass
import com.clearboxmedia.couchdb.domain.CouchDomainClassArtefactHandler
import net.sf.json.JSON
import net.sf.json.JSONObject
import net.sf.json.JSONSerializer
import net.sf.json.JsonConfig
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.codehaus.groovy.grails.plugins.DomainClassPluginSupport
import org.codehaus.groovy.grails.support.SoftThreadLocalMap
import org.codehaus.groovy.grails.validation.GrailsDomainClassValidator
import org.jcouchdb.db.Database
import org.jcouchdb.db.Options
import org.jcouchdb.document.Attachment
import org.jcouchdb.document.DesignDocument
import org.jcouchdb.exception.NotFoundException
import org.jcouchdb.util.CouchDBUpdater
import org.springframework.beans.factory.config.MethodInvokingFactoryBean
import org.springframework.context.ApplicationContext
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.Errors

/**
 *
 * @author Warner Onstine, Cory Hacking
 */
public class CouchdbPluginSupport {

    static final DOMAIN_CLASS_MAP = new HashMap<String, CouchDomainClass>();
    static final PROPERTY_INSTANCE_MAP = new SoftThreadLocalMap()

    static def doWithSpring = {ApplicationContext ctx ->

        updateCouchViews(application)

        // register our CouchDomainClasses artefacts that weren't already picked up by grails
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
            def ds = application.config.couchdb
            Database db = new Database(ds.host, ds.port, ds.database)

            DOMAIN_CLASS_MAP[domainClass.getFullName()] = domainClass

            addInstanceMethods(application, domainClass, ctx, db)
            addStaticMethods(application, domainClass, ctx, db)

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
            def ds = application.config.couchdb

            // Note that any map / reduce functions that are in couchdb but NOT here get
            // removed when updating.  I believe this is by design in jcouchdb.
            CouchDBUpdater updater = new CouchDBUpdater();
            updater.setDatabase(new Database(ds.host, ds.port, ds.database));
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

            if (validate()) {

                // create our couch document that can be serialized to json properly
                CouchDocument doc = convertToCouchDocument(delegate)

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
            couchdb.delete getDocumentId(domainClass, delegate), getDocumentVersion(domainClass, delegate)
        }

        metaClass.getAttachment = {Serializable attachmentId ->
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

        metaClass.'static'.get = {Serializable docId ->
            try {
                def json = couchdb.getDocument(JSONObject.class, docId.toString())
                def domain = convertToDomainObject(domainClass, json)

                return domain

            } catch (NotFoundException e) {
                // fall through to return null
            }

            return null
        }

        // Foo.exists(1)
        metaClass.'static'.exists = {Serializable docId ->
            get(docId) != null
        }

        metaClass.'static'.delete = {Serializable docId, String version ->
            couchdb.delete docId.toString(), version
        }

        metaClass.'static'.bulkSave = {List documents ->
            return bulkSave(documents, false)
        }

        metaClass.'static'.bulkSave = {List documents, Boolean allOrNothing ->
            def couchDocuments = new ArrayList()

            documents.each {doc ->
                couchDocuments << convertToCouchDocument(doc)

            }

            return couchdb.bulkCreateDocuments(couchDocuments, allOrNothing)
        }

        metaClass.'static'.bulkDelete = {List documents ->
            return bulkDelete(documents, false)
        }

        metaClass.'static'.bulkDelete = {List documents, boolean allOrNothing ->
            def couchDocuments = new ArrayList()

            documents.each {doc ->
                couchDocuments << convertToCouchDocument(doc)

            }

            return couchdb.bulkDeleteDocuments(couchDocuments, allOrNothing)
        }

        metaClass.'static'.getAttachment = {Serializable docId, String attachmentId ->
            return couchdb.getAttachment(docId.toString(), attachmentId)
        }

        metaClass.'static'.saveAttachment = {Serializable docId, String version, String attachmentId, String contentType, byte[] data ->
            return couchdb.createAttachment(docId.toString(), version, attachmentId, contentType, data)
        }

        metaClass.'static'.saveAttachment = {Serializable docId, String version, String attachmentId, String contentType, InputStream is, long length ->
            return couchdb.createAttachment(docId.toString(), version, attachmentId, contentType, is, length)
        }

        metaClass.'static'.deleteAttachment = {Serializable docId, String version, String attachmentId ->
            return couchdb.deleteAttachment(docId.toString(), version, attachmentId)
        }

        metaClass.'static'.findAll = {Map o = [:] ->
            return findAll(getOptions(o))
        }

        metaClass.'static'.findAll = {Options o ->
            return couchdb.listDocuments(o, null).getRows()
        }

        metaClass.'static'.findAllByUpdateSequence = {Map o = [:] ->
            return findAllByUpdateSequence(getOptions(o))
        }

        metaClass.'static'.findAllByUpdateSequence = {Options o ->
            return couchdb.listDocumentsByUpdateSequence(o, null).getRows()
        }

        metaClass.'static'.findByView = {String viewName, Map o = [:] ->
            return findByView(viewName, getOptions(o))
        }

        metaClass.'static'.findByView = {String viewName, Options o ->
            return couchdb.queryView(viewName, Map.class, o, null).getRows()
        }

        metaClass.'static'.findByViewAndKeys = {String viewName, List keys, Map o = [:] ->
            return findByViewAndKeys(viewName, keys, getOptions(o));
        }

        metaClass.'static'.findByViewAndKeys = {String viewName, List keys, Options o ->
            return couchdb.queryViewByKeys(viewName, Map.class, keys, o, null).getRows();
        }

        metaClass.'static'.getDesignDocument = {Serializable id ->
            try {
                return couchdb.getDesignDocument(id.toString())
            } catch (NotFoundException e) {
                // fall through to return null
            }

            return null
        }

        metaClass.'static'.saveDesignDocument = {DesignDocument doc ->
            return couchdb.createOrUpdateDocument(doc)
        }
    }

    private static addValidationMethods(GrailsApplication application, CouchDomainClass dc, ApplicationContext ctx) {
        def metaClass = dc.metaClass
        def domainClass = dc

        metaClass.'static'.getConstraints = {->
            domainClass.constrainedProperties
        }

        metaClass.getConstraints = {->
            domainClass.constrainedProperties
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

    private static Object convertToDomainObject(CouchDomainClass dc, JSON json) {
        JsonConfig jsonConfig = new CouchJsonConfig();
        jsonConfig.setRootClass(dc.clazz);

        def doc = JSONSerializer.toJava(json, jsonConfig);

        def id = dc.getIdentifier()
        if (id) {
            doc[id.name] = json['_id']
        }

        def version = dc.getVersion()
        if (version) {
            doc[version.name] = json['_rev']
        }

        def att = dc.getAttachments()
        if (att) {
            doc[att.name] = json['_attachments']
        }

        return doc
    }

    private static CouchDocument convertToCouchDocument(Object domain) {
        return convertToCouchDocument(DOMAIN_CLASS_MAP[domain.getClass().getName()], domain)
    }

    private static CouchDocument convertToCouchDocument(CouchDomainClass dc, Object domain) {
        CouchDocument doc = new CouchDocument()

        if (dc) {

            // set the document type if it is enabled set it first, so that it can be
            //  overridden by an actual type property if there is one...
            if (dc.type) {
                doc["type"] = dc.type
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

    private static Options getOptions(Map o) {
        def options = new Options()

        if (o) {

            // convert the map to options; some options need to be encoded, but the
            //  Options(Map) constructor doesn't do it properly so we're doing it manually.
            o.each {key, value ->
                if (key == "key" || key == "startkey" || key == "endkey") {
                    options.put(key, value)
                } else {
                    options.putUnencoded(key, value)
                }
            }
        }

        return options
    }
}