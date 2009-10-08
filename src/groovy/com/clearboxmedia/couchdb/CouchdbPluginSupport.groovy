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
                MetaClass mc = clazz.getMetaClass()

                CouchdbGrailsDomainClass cdc = new CouchdbGrailsDomainClass(clazz)
                couchdbDomainClasses[dc.getFullName()] = cdc

                application.addArtefact(CouchdbDomainClassArtefactHandler.TYPE, cdc)

                clazz.metaClass {

                    //
                    // Static Methods
                    //
                    'static' {

                        get {Serializable docId ->
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
                        exists {Serializable docId ->
                            get(docId) != null
                        }

                        delete {Serializable docId, String version ->
                            db.delete docId.toString(), version
                        }

                        bulkSave {List documents ->
                            return db.bulkCreateDocuments(documents, false)
                        }

                        bulkSave {List documents, Boolean allOrNothing ->
                            def couchDocuments = new ArrayList()

                            documents.each {doc ->
                                couchDocuments << convertToCouchDocument(doc)

                            }

                            return db.bulkCreateDocuments(couchDocuments, allOrNothing)
                        }

                        bulkDelete {List documents ->
                            return db.bulkDeleteDocuments(documents, false)
                        }

                        bulkDelete {List documents, boolean allOrNothing ->
                            def couchDocuments = new ArrayList()

                            documents.each {doc ->
                                couchDocuments << convertToCouchDocument(doc)

                            }

                            return db.bulkDeleteDocuments(couchDocuments, allOrNothing)
                        }

                        createAttachment {Serializable docId, String version, String attachmentId, String contentType, byte[] data ->
                            return db.createAttachment(docId.toString(), version, attachmentId, contentType, data)
                        }

                        updateAttachment {Serializable docId, String version, String attachmentId, String contentType, byte[] data ->
                            return db.updateAttachment(docId.toString(), version, attachmentId, contentType, data)
                        }

                        createAttachment {Serializable docId, String version, String attachmentId, String contentType, InputStream is, long length ->
                            return db.createAttachment(docId.toString(), version, attachmentId, contentType, is, length)
                        }

                        updateAttachment {Serializable docId, String version, String attachmentId, String contentType, InputStream is, long length ->
                            return db.updateAttachment(docId.toString(), version, attachmentId, contentType, is, length)
                        }

                        deleteAttachment {Serializable docId, String version, String attachmentId ->
                            return db.deleteAttachment(docId.toString(), version, attachmentId)
                        }

                        getAttachment {Serializable docId, String attachmentId ->
                            return db.getAttachment(docId.toString(), attachmentId)
                        }

                        list {Map o = [:] ->
                            return db.listDocuments(getOptions(o), null).getRows()
                        }

                        listByUpdateSequence {Map o = [:] ->
                            return db.listDocumentsByUpdateSequence(getOptions(o), null).getRows()
                        }
                    }

                    //
                    // Class Methods
                    //
                    save {->
                        save(null)
                    }

                    save {Map args = [:] ->

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

                    delete {->
                        db.delete getDocumentId(cdc, delegate), getDocumentVersion(cdc, delegate)
                    }

                    createAttachment {Serializable attachmentId, String contentType, byte[] data ->
                        return db.createAttachment(getDocumentId(cdc, delegate), getDocumentVersion(cdc, delegate), attachmentId.toString(), contentType, data)
                    }

                    updateAttachment {Serializable attachmentId, String contentType, byte[] data ->
                        return db.updateAttachment(getDocumentId(cdc, delegate), getDocumentVersion(cdc, delegate), attachmentId.toString(), contentType, data)
                    }

                    createAttachment {Serializable attachmentId, String contentType, InputStream is, long length ->
                        return db.createAttachment(getDocumentId(cdc, delegate), getDocumentVersion(cdc, delegate), attachmentId.toString(), contentType, is, length)
                    }

                    updateAttachment {Serializable attachmentId, String contentType, InputStream is, long length ->
                        return db.updateAttachment(getDocumentId(cdc, delegate), getDocumentVersion(cdc, delegate), attachmentId.toString(), contentType, is, length)
                    }

                    deleteAttachment {Serializable attachmentId ->
                        return db.deleteAttachment(getDocumentId(cdc, delegate), getDocumentVersion(cdc, delegate), attachmentId.toString())
                    }

                    getAttachment {Serializable attachmentId ->
                        return db.getAttachment(getDocumentId(cdc, delegate), attachmentId.toString())
                    }
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
        return (o) ? new Options(o) : new Options()
    }
}