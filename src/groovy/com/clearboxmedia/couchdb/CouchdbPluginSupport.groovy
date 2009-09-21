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
        for (GrailsDomainClass dc in application.domainClasses) {
            def clazz = dc.clazz
            println "clazz is ${clazz}"
            if (clazz.isAnnotationPresent(CouchDBEntity)) {
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

                            println "delegate name is ${delegate.name}"
                            if (delegate.validate()) {
                                println "delegate properties ${delegate.propertyMap}"
                                //for now have couch create our id for this object, do want to customize this later though
                                if (delegate.id != null && delegate.id != "") {
                                    def doc = delegate.getProperties()
                                    doc["type"] = delegate.type
                                    def id = db.createDocument(doc)
                                    delegate.id = id;
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


}