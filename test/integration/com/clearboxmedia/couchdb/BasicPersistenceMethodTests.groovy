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

import org.acme.Project
import org.acme.Task
import org.jcouchdb.document.DesignDocument
import org.jcouchdb.document.DocumentInfo
import org.jcouchdb.document.View

/**
 * @author Warner Onstine, Cory Hacking
 */
public class BasicPersistenceMethodTests extends GroovyTestCase {

    static transactional = false

    void testDesignDocument() {

        def design = Task.getDesignDocument("tasks")
        if (!design) {
            design = new DesignDocument("tasks");
        }

        if (!design.views["byName"]) {

            // add a temporary "open" view
            design.addView("byName", new View("function(doc) { if (doc.type == 'project-task') { emit(doc.name,1); }}"))

            // save the design document
            Task.saveDesignDocument(design)
        }
    }

    void testValidation() {
        def p = new Project(name: "")

        assertNull "should not have validated", p.save()
        assertEquals "should have 1 error", p.errors.allErrors.size(), 1
        assertEquals "name should be in error", p.errors.allErrors[0].field, "name"
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

            p.save()
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
            t.name = "task-${i}"
            t.projectId = p.id
            t.startDate = new Date()
            t.description = "This is the description for task ${i}."

            bulkDocuments << t
        }

        // bulk save all of the documents
        List<DocumentInfo> result = Project.bulkSave(bulkDocuments)

        // verify that they all saved
        result.each {DocumentInfo info ->
            assertNull "Document ${info.id} should have been bulk-saved successfully", info.error
        }
    }

    void testFind() {

        def result = Project.findAll()
        result.each {info ->
            println info
        }

        result = Project.findAllByUpdateSequence()
        result.each {info ->
            println info
        }

        result = Project.findByView("openTasks/byName")
        assertEquals "should have found 20 open tasks", result.size(), 20
        result.each {info ->
            println info
        }

        result = Project.findByView("openTasks/byName", ['startkey': "task-1", 'endkey': "task-10"])
        assertEquals "should have found 2 open tasks", result.size(), 2
        result.each {info ->
            println info
        }

        result = Project.findByViewAndKeys("openTasks/byName", ["task-15"])
        assertEquals "should have found 1 open task", result.size(), 1
        result.each {info ->
            println info
            assertEquals "should have found task #15", info.key, "task-15"
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
        List<DocumentInfo> result = Project.bulkDelete(bulkDocuments)

        result.each {DocumentInfo info ->
            assertNull "Document ${info.id} should have been bulk-deleted successfully", info.error
        }
    }
}