package com.clearboxmedia.couchdb

import org.springframework.context.ApplicationContext
import org.codehaus.groovy.grails.commons.GrailsDomainClass
import com.clearboxmedia.couchdb.domain.CouchdbGrailsDomainClass
import com.clearboxmedia.couchdb.domain.CouchdbDomainClassArtefactHandler
import org.jcouchdb.db.Database


public class CouchdbPluginSupport {

    static couchdbProps = [:]
    static couchdbConfigClass

    //temporary
    Database db = new Database("localhost", "gorm-couchdb-test")

    static doWithApplicationContext = {ApplicationContext applicationContext ->
        for (GrailsDomainClass dc in application.domainClasses) {
            def clazz = dc.clazz
            if (clazz.isAnnotationPresent(CouchDBEntity)) {
                Class entityClass = dc.class
                application.addArtefact(CouchdbDomainClassArtefactHandler.TYPE, new CouchdbGrailsDomainClass(entityClass))

                //now we can start registering the standard methods
                entityClass.metaClass {
                    'static' {
                        //Foo.get(1)
                        get {Serializable id ->
                            db.getDocument(entityClass, id)
                        }

                        //Foo.exists(1)
                        exists {Serializable id ->
                            get(id) != null
                        }

                        save {Map args = [:] ->
                            if (delegate.validate()) {
                                println "delegate is ${delegate}"
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