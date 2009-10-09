package com.clearboxmedia.couchdb

import com.clearboxmedia.couchdb.domain.CouchDocument
import com.clearboxmedia.couchdb.domain.CouchdbDomainClassArtefactHandler
import com.clearboxmedia.couchdb.domain.CouchdbGrailsDomainClass
import net.sf.json.JSON
import net.sf.json.JSONObject
import net.sf.json.JSONSerializer
import net.sf.json.JsonConfig
import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.jcouchdb.db.Database
import org.jcouchdb.db.Options
import org.jcouchdb.document.Attachment
import org.jcouchdb.document.DesignDocument
import org.jcouchdb.exception.NotFoundException

public class CouchdbPluginSupport {

    static couchdbProps = [:]
    static couchdbConfigClass

    static couchdbDomainClasses = [:]

    static doWithDynamicMethods = {applicationContext ->

        def ds = application.config.couchdb

        Database db = new Database(ds.host, ds.port, ds.database)
        application.domainClasses.each {GrailsDomainClass dc ->
            def clazz = dc.clazz

            if (clazz.isAnnotationPresent(CouchEntity)) {
                CouchdbGrailsDomainClass cdc = new CouchdbGrailsDomainClass(clazz)
                couchdbDomainClasses[dc.getFullName()] = cdc

                application.addArtefact(CouchdbDomainClassArtefactHandler.TYPE, cdc)

                MetaClass mc = clazz.getMetaClass()

                //
                // Class Methods
                //
                mc.save = {->
                    save(null)
                }

                mc.save = {Map args = [:] ->

                    if (delegate.validate()) {

                        // create our couch document that can be serialized to json properly
                        CouchDocument doc = convertToCouchDocument(delegate)

                        // save the document
                        db.createOrUpdateDocument doc

                        // set the return id and revision on the domain object
                        def id = cdc.getIdentifier()
                        if (id) {
                            delegate[id.name] = doc.id
                        }

                        def rev = cdc.getVersion()
                        if (rev) {
                            delegate[rev.name] = doc.revision
                        }

                        return delegate
                    }

                    return null
                }

                mc.delete = {->
                    db.delete getDocumentId(cdc, delegate), getDocumentVersion(cdc, delegate)
                }

                mc.getAttachment = {Serializable attachmentId ->
                    return db.getAttachment(getDocumentId(cdc, delegate), attachmentId.toString())
                }

                mc.saveAttachment = {Serializable attachmentId, String contentType, byte[] data ->
                    return db.createAttachment(getDocumentId(cdc, delegate), getDocumentVersion(cdc, delegate), attachmentId.toString(), contentType, data)
                }

                mc.saveAttachment = {Serializable attachmentId, String contentType, InputStream is, long length ->
                    return db.createAttachment(getDocumentId(cdc, delegate), getDocumentVersion(cdc, delegate), attachmentId.toString(), contentType, is, length)
                }

                mc.deleteAttachment = {Serializable attachmentId ->
                    return db.deleteAttachment(getDocumentId(cdc, delegate), getDocumentVersion(cdc, delegate), attachmentId.toString())
                }

                //
                // Static Methods
                //
                mc.'static'.get = {Serializable docId ->
                    try {
                        def json = db.getDocument(JSONObject.class, docId.toString())
                        def domain = convertToDomainObject(cdc, json)

                        return domain

                    } catch (NotFoundException e) {
                        // fall through to return null
                    }

                    return null
                }

                // Foo.exists(1)
                mc.'static'.exists = {Serializable docId ->
                    get(docId) != null
                }

                mc.'static'.delete = {Serializable docId, String version ->
                    db.delete docId.toString(), version
                }

                mc.'static'.bulkSave = {List documents ->
                    return bulkSave(documents, false)
                }

                mc.'static'.bulkSave = {List documents, Boolean allOrNothing ->
                    def couchDocuments = new ArrayList()

                    documents.each {doc ->
                        couchDocuments << convertToCouchDocument(doc)

                    }

                    return db.bulkCreateDocuments(couchDocuments, allOrNothing)
                }

                mc.'static'.bulkDelete = {List documents ->
                    return bulkDelete(documents, false)
                }

                mc.'static'.bulkDelete = {List documents, boolean allOrNothing ->
                    def couchDocuments = new ArrayList()

                    documents.each {doc ->
                        couchDocuments << convertToCouchDocument(doc)

                    }

                    return db.bulkDeleteDocuments(couchDocuments, allOrNothing)
                }

                mc.'static'.getAttachment = {Serializable docId, String attachmentId ->
                    return db.getAttachment(docId.toString(), attachmentId)
                }

                mc.'static'.saveAttachment = {Serializable docId, String version, String attachmentId, String contentType, byte[] data ->
                    return db.createAttachment(docId.toString(), version, attachmentId, contentType, data)
                }

                mc.'static'.saveAttachment = {Serializable docId, String version, String attachmentId, String contentType, InputStream is, long length ->
                    return db.createAttachment(docId.toString(), version, attachmentId, contentType, is, length)
                }

                mc.'static'.deleteAttachment = {Serializable docId, String version, String attachmentId ->
                    return db.deleteAttachment(docId.toString(), version, attachmentId)
                }

                mc.'static'.findAll = {Map o = [:] ->
                    return findAll(getOptions(o))
                }

                mc.'static'.findAll = {Options o ->
                    return db.listDocuments(o, null).getRows()
                }

                mc.'static'.findAllByUpdateSequence = {Map o = [:] ->
                    return findAllByUpdateSequence(getOptions(o))
                }

                mc.'static'.findAllByUpdateSequence = {Options o ->
                    return db.listDocumentsByUpdateSequence(o, null).getRows()
                }

                mc.'static'.findByView = {String viewName, Map o = [:] ->
                    return findByView(viewName, getOptions(o))
                }

                mc.'static'.findByView = {String viewName, Options o ->
                    return db.queryView(viewName, Map.class, o, null).getRows()
                }

                mc.'static'.findByViewAndKeys = {String viewName, List keys, Map o = [:] ->
                    return findByViewAndKeys(viewName, keys, getOptions(o));
                }

                mc.'static'.findByViewAndKeys = {String viewName, List keys, Options o ->
                    return db.queryViewByKeys(viewName, Map.class, keys, o, null).getRows();
                }

                mc.'static'.getDesignDocument = {Serializable id ->
                    try {
                        return db.getDesignDocument(id.toString())
                    } catch (NotFoundException e) {
                        // fall through to return null
                    }
                }

                mc.'static'.saveDesignDocument = {DesignDocument doc ->
                    return db.createDocument(doc)
                }
            }
        }
    }

    private static Object convertToDomainObject(CouchdbGrailsDomainClass cdc, JSON json) {
        JsonConfig jsonConfig = new CouchJsonConfig();
        jsonConfig.setRootClass(cdc.clazz);

        def doc = JSONSerializer.toJava(json, jsonConfig);

        def id = cdc.getIdentifier()
        if (id) {
            doc[id.name] = json['_id']
        }

        def version = cdc.getVersion()
        if (version) {
            doc[version.name] = json['_rev']
        }

        def att = cdc.getAttachments()
        if (att) {
            doc[att.name] = json['_attachments']
        }

        return doc
    }

    private static CouchDocument convertToCouchDocument(Object domain) {
        return convertToCouchDocument(couchdbDomainClasses[domain.getClass().getName()], domain)
    }

    private static CouchDocument convertToCouchDocument(CouchdbGrailsDomainClass cdc, Object domain) {
        CouchDocument doc = new CouchDocument()

        if (cdc) {

            // set the document type if it is enabled set it first, so that it can be
            //  overridden by an actual type property if there is one...
            if (cdc.type) {
                doc["type"] = cdc.type
            }

            // set all of the persistant properties.
            cdc.getPersistantProperties().each() {prop ->
                doc[prop.name] = domain[prop.name]
            }

            // set the document id, revision, and attachments... do it last so that the
            //  annotated properties override any other ones that may be there.  Especially
            //  needed if hibernate is already enabled as id and version fields are created,
            //  but may not be used.
            doc.id = getDocumentId(cdc, domain)
            doc.revision = getDocumentVersion(cdc, domain)
            doc.attachments = getDocumentAttachments(cdc, domain)

        } else {

            // todo: what do we do with an object we don't know how to convert?

        }

        return doc
    }

    private static String getDocumentId(CouchdbGrailsDomainClass cdc, Object domain) {
        def id = cdc.getIdentifier()
        if (id) {
            domain[id.name]
        }
    }

    private static String getDocumentVersion(CouchdbGrailsDomainClass cdc, Object domain) {
        def version = cdc.getVersion()
        if (version) {
            domain[version.name]
        }
    }

    private static Map<String, Attachment> getDocumentAttachments(CouchdbGrailsDomainClass cdc, Object domain) {
        def att = cdc.getAttachments()
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