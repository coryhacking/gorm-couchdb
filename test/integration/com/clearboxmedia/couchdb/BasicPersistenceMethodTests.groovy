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

        p.startDate = new Date()
        p.lastUpdated = new Date()

        p.save()

        assertNotNull "should have saved new project", p
        assertNotNull "should have retrieved id of new project", p.id
        assertNotNull "should have retrieved revision of new project", p.rev

        println "id and revision from saved project is ${p.id} ${p.rev}"
        
        def p2 = Project.get(p.id)

        assertNotNull "should have retrieved a project", p2
        assertEquals "project ids should be equal", p.id, p2.id
        assertEquals "project revisions should be equal", p.rev, p2.rev
    }

    void testUpdate() {

        def id = "testUpdate"

        def p = Project.get(id)
        if (!p) {
            p = new Project()
            
            p.id = id
            p.startDate = new Date()
            p.name = "Test Project"

            println "project ${p.id} is new."
        } else {
            println "project ${p.id} revision ${p.rev} was read."
        }

        p.lastUpdated = new Date()

        p.save()

        assertNotNull "should have saved a project", p
    }

}