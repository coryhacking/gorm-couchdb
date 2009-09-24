package com.clearboxmedia.couchdb

import org.springframework.context.ApplicationContext
import org.codehaus.groovy.grails.commons.GrailsDomainClass
import com.clearboxmedia.couchdb.domain.CouchdbGrailsDomainClass
import com.clearboxmedia.couchdb.domain.CouchdbDomainClassArtefactHandler
import org.jcouchdb.db.Database


public class CouchdbPluginSupport {

    static couchdbProps = [:]
    static couchdbConfigClass



    static doWithApplicationContext = {ApplicationContext applicationContext ->
        //temporary
        Database db = new Database("localhost", "gorm-couchdb-test")
        for (CouchdbGrailsDomainClass dc in application.domainClasses) {
            def clazz = dc.clazz
            if (clazz.isAnnotationPresent(CouchDBEntity)) {
            println "clazz is ${clazz}"
                application.addArtefact(CouchdbDomainClassArtefactHandler.TYPE, new CouchdbGrailsDomainClass(clazz))

                //now we can start registering the standard methods
                clazz.metaClass {
                    'static' {
                        //Foo.get(1)
                        get {Serializable id ->
                            db.getDocument(clazz, id)
                        }

                        //Foo.exists(1)
                        exists {Serializable id ->
                            get(id) != null
                        }

                        save {Map args = [:] ->
                            if(!clazz.isAnnotationPresent(CouchDBEntity)) {
                                println "returning delegate ${delegate.name}"
                                return delegate
                            }
                            println "delegate name is ${delegate.name}"
                            if (delegate.validate()) {
                                //for now have couch create our id for this object, do want to customize this later though
                                if (delegate.id != null && delegate.id != "") {
                                    Map document = CouchdbPluginSupport.createDocumentMap(dc, delegate, false)
                                    db.createDocument(document)
                                } else {
                                    Map document = CouchdbPluginSupport.createDocumentMap(dc, delegate, true)
                                    def id = db.createDocument(document)
                                    delegate.id = id
                                }

                                return delegate
                            } else {
                                return null
                            }
                        }

                    }
                }
            } else {
                continue
            }
        }

    }

    private static Map createDocumentMap(GrailsDomainClass dc, GroovyObject delegate, boolean newDocument) {
        println "calling createDocumentMap"
        Map document = [:]
        if (newDocument) {
            document["id"] = delegate.id
        } else {
            document["_id"] = delegate.id
        }
        document["type"] = delegate.type
        println "dc properties are ${dc.getProperties()}"
        dc.getProperties().each() {key, value ->
            println "key is ${key}, delgate value is " + delegate."${key}"
            document["${key}"] = delegate."${key}"
        }
        return document
    }


}