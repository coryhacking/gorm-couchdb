package com.clearboxmedia.couchdb

import org.acme.Project


public class BasicPersistenceMethodTests extends GroovyTestCase {
    static transactional = false

    void testValidation() {
        def p = new Project(name: "")
        assertNull "should not have validated", p.save()

    }

    void testSaveAndGet() {

        def p = new Project(name: "InConcert")
        p.save(flush:true)


        println "id from saved project is ${p.id}"
        //p = Project.get(p.id)

        assertNotNull "should have retrieved a project", p
    }

}