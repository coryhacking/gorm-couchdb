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
package org.codehaus.groovy.grails.plugins.couchdb.test

import org.acme.Project
import org.acme.Task
import org.jcouchdb.document.DocumentInfo

/**
 * Test the bulkSave and bulkDelete operations
 *
 * @author Cory Hacking
 */
class BulkOperationsTests extends GroovyTestCase {

    void testBulkSave() {
        def bulkDocuments = []

        Date startDate

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

        assertEquals "should have 1 project", 1, Project.count()

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
            t.estimatedHours = i

            if (!startDate) {
                startDate = t.startDate
            }

            bulkDocuments << t
        }

        // bulk save all of the documents
        def result = Project.bulkSave(bulkDocuments)

        // verify that they all saved
        result.each {DocumentInfo info ->
            assertNull "Document ${info.id} should have been bulk-saved successfully [${info.error}]", info.error
        }

        def t1 = Task.get(result[10].id)
        assertNotNull "bulkSave should have set dateCreated", t1.dateCreated
        assertNotNull "bulkSave should have set lastUpdated", t1.lastUpdated

        def t2 = Task.get(t1.taskId)

        bulkDocuments.clear()
        bulkDocuments << t2

        Thread.currentThread().sleep(1000);

        result = Task.bulkSave(bulkDocuments)
        t2 = Task.get(result[0].id)

        assertEquals "project id's should be the same", t1.taskId, t2.taskId
        assertTrue "lastUpdated should be different", !t1.lastUpdated.equals(t2.lastUpdated)
        assertTrue "t2.lastUpdated should be after t1.lastUpdated", t2.lastUpdated.after(t1.lastUpdated)

        // test date finder here
        result = Task.findOpenTasksByStartDate(startDate)
        assertTrue "should have found at least 1 task (depends upon timing)", result.size() >= 1

		// test a finder that doesn't have named values
		result = Project.findById(p.id)
		assertEquals "should have found one project", 1, result.size()
		assertEquals "should have found project [${p.id}]", p.id, result[0].id
		assertTrue "value should be a string", result[0].value instanceof String
		assertEquals "value should be = '1'", "1", result[0].value
    }

    void testFinders() {

        def result = Project.findAll()

        result = Task.list()
        result.each {info ->
            println info
            assertTrue "startDate should be a Date object", info.startDate instanceof Date
            assertTrue "estimatedHours should be an Integer object", info.estimatedHours instanceof Integer
        }
        assertEquals "should have found 20 tasks", 20, result.size()

        result = Task.list(max: 10)
        assertEquals "should have found 10 tasks", 10, result.size()

		result = Task.list(max: 10, include_docs: true)
		assertEquals "should have found 10 tasks", 10, result.size()
		assertNotNull "results should include docs", result[0].document
		result.each {info ->
			println info
			assertNotNull "description should not be null", info.description
			assertTrue "estimatedHours should be > 0", info.estimatedHours > 0
		}

        result = Task.listOpenTasksByName("order": "desc")
        assertEquals "should have found 20 open tasks", 20, result.size()

        assertEquals "should have counted 20 open tasks", 20, Task.countOpenTasks()

        result = Task.findOpenTasksByName("offset": 5, "max": 10)
        assertEquals "should have found 10 open tasks", 10, result.size()

        result = Task.findOpenTasksByName('startkey': "task-1", 'endkey': "task-10")
        assertEquals "should have found 2 open tasks", 2, result.size()
        result.each {info ->
            assertNotNull "task name should not be null", info.name
            assertNotNull "task startDate should not be null", info.startDate
            assertTrue "task start date should be a date", info.startDate instanceof Date
        }

        def descending = Task.queryView("openTasksByName", ['startkey': "task-10", 'endkey': "task-1", "order": "desc"])
        assertEquals "should have found 2 open tasks", descending.size(), 2
        assertEquals "should be in reverse order", result[0].id, descending[1].id
        assertEquals "should be in reverse order", result[1].id, descending[0].id

        result = Task.findOpenTasksByName("task-15", "task-16", "task-17", "order": "desc")
        assertEquals "should have found 3 open tasks", 3, result.size()
        assertEquals "should have found task #15", "task-15", result[0].key

        result = Task.findByProjectIdAndName(["gorm-couchdb", "task-1"], include_docs: true)
		assertEquals "should have found 1 tasks", 1, result.size()
        assertEquals "should have found 'gorm-couchdb-task-1'", "gorm-couchdb-task-1", result[0].id
		result.each {info ->
			println info
			assertNotNull "description should not be null", info.description
			assertTrue "estimatedHours should be > 0", info.estimatedHours > 0
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
