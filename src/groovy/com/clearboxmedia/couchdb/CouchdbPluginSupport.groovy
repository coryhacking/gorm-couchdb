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
import org.jcouchdb.exception.NotFoundException

public class CouchdbPluginSupport {

    static couchdbProps = [:]
    static couchdbConfigClass

    static doWithDynamicMethods = {applicationContext ->

        def ds = application.config.couchdb

        Database db = new Database(ds.host, ds.port, ds.database)
        application.domainClasses.each {GrailsDomainClass dc ->
            def clazz = dc.clazz

            if (clazz.isAnnotationPresent(CouchDBEntity)) {
                MetaClass mc = clazz.getMetaClass()
                CouchdbGrailsDomainClass cdc = new CouchdbGrailsDomainClass(clazz)

                application.addArtefact(CouchdbDomainClassArtefactHandler.TYPE, cdc)

                clazz.metaClass {
                    'static' {

                        // Foo.get(1)
                        get {Serializable id ->
                            try {
                                def json = db.getDocument(JSONObject.class, id.toString())
                                def domain = convertToDomainObject(cdc, json)

                                return domain

                            } catch (NotFoundException e) {
                                // fall through to return null
                            }

                            return null
                        }

                        // Foo.exists(1)
                        exists {Serializable id ->
                            get(id) != null
                        }
                    }
                }

                mc.save {->
                    save(null)
                }

                mc.save {Map args = [:] ->

                    if (delegate.validate()) {

                        // create our couch document that can be serialized to json properly
                        CouchDocument doc = convertToCouchDocument(cdc, delegate)

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
                    } else {
                        return null
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

        def rev = cdc.getVersion()
        if (rev) {
            doc[rev.name] = json['_rev']
        }

        // todo: handle attachments

        return doc
    }

    private static CouchDocument convertToCouchDocument(CouchdbGrailsDomainClass cdc, GroovyObject domain) {
        CouchDocument doc = new CouchDocument()

        cdc.getPersistantProperties().each() {prop ->
            doc[prop.name] = domain[prop.name]
        }

        def id = cdc.getIdentifier()
        if (id) {
            doc.id = domain[id.name]
        }

        def rev = cdc.getVersion()
        if (rev) {
            doc.revision = domain[rev.name]
        }

        // todo: handle attachments

        return doc
    }
}