package com.clearboxmedia.couchdb

import org.acme.Project
import org.acme.Task
import org.jcouchdb.document.DocumentInfo

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
        assertNotNull "should have retrieved revision of new project", p.version

        println "id and revision from saved project is ${p.id} ${p.version}"

        def p2 = Project.get(p.id)

        assertNotNull "should have retrieved a project", p2
        assertEquals "project ids should be equal", p.id, p2.id
        assertEquals "project revisions should be equal", p.version, p2.version

        p.delete()
    }

    void testUpdateAndDelete() {
        def id = "gorm-couchdb"

        def p = Project.get(id)
        if (!p) {
            p = new Project()

            p.id = id
            p.startDate = new Date()
            p.name = "Test Project"

            println "project ${p.id} is new."
        } else {
            println "project ${p.id} revision ${p.version} was read."
        }

        p.lastUpdated = new Date()
        p.save()

        assertNotNull "should have saved a project", p
        assertEquals "should have saved project with id = '${id}'", p.id, id

        p.delete()
    }

    void testBulkSave() {
        def bulkDocuments = []
        def id = "gorm-couchdb"

        // get (or create) our test project
        def p = Project.get(id)
        if (!p) {
            p = new Project()

            p.id = id
            p.name = "A New Test Project w/tasks"
            p.startDate = new Date()
            p.save()
        }

        // update the last updated date and add to our bulk documents
        p.lastUpdated = new Date()
        bulkDocuments << p

        // create 20 tasks and "relate" to the original project
        (1..20).each {i ->
            def t = new Task()

            t.taskId = "${p.id}-task-${i}"
            t.projectId = p.id
            t.startDate = new Date()
            t.description = "This is the description for task ${i}."

            bulkDocuments << t
        }

        // bulk save all of the documents
        List<DocumentInfo> result = Project.bulkSave(bulkDocuments, true)

        // verify that they all saved
        result.each {DocumentInfo info ->
            assertNull "Document ${info.id} should have been bulk-saved successfully", info.error
        }
    }

    void testList() {

        def result = Project.list()
        result.each {info ->
            print info
        }

        result = Project.listByUpdateSequence()
        result.each {info ->
            print info
        }
    }

    void testBulkDelete() {
        def bulkDocuments = []
        def id = "gorm-couchdb"

        // get our test project and add it to the bulk documents list
        def p = Project.get(id)
        if (p) {
            bulkDocuments << p
        }

        // read each of our tasks and add to bulk documents list
        (1..20).each {i ->
            def t = Task.get("${id}-task-${i}")
            if (t) {
                bulkDocuments << t
            }
        }

        // do our bulk delete.
        List<DocumentInfo> result = Project.bulkDelete(bulkDocuments, true)

        result.each {DocumentInfo info ->
            assertNull "Document ${info.id} should have been bulk-deleted successfully", info.error
        }
    }
}