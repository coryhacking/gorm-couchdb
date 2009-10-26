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
package org.acme

import org.acme.Contact.Gender
import org.jcouchdb.document.DesignDocument
import org.jcouchdb.document.DocumentInfo
import org.jcouchdb.document.View

/**
 * @author Warner Onstine, Cory Hacking
 */
public class BasicPersistenceMethodTests extends GroovyTestCase {

    static transactional = false

    void testDesignDocument() {

        def design = Task.getDesignDocument()
        if (!design) {
            design = new DesignDocument();
        }

        if (!design.views["allByName"]) {

            // add a temporary "open" view
            design.addView("allByName", new View("function(doc) { if (doc.type == 'project-task') { emit(doc.name, null); }}"))

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

        def p1 = new Project(name: "InConcert")

        p1.startDate = new Date()

        p1.save()

        assertNotNull "should have saved new project", p1
        assertNotNull "should have retrieved id of new project", p1.id
        assertNotNull "should have retrieved revision of new project", p1.version
        assertNotNull "should have set dateCreated", p1.dateCreated
        assertNotNull "should have set lastUpdated", p1.lastUpdated

        println "id and revision from saved project is ${p1.id}${p1.version}"

        def p2 = Project.get(p1.id)

        assertNotNull "should have retrieved a project", p2
        assertEquals "project ids should be equal", p1.id, p2.id
        assertEquals "project revisions should be equal", p1.version, p2.version

        Thread.currentThread().sleep(1000);

        p2.save()
        assertTrue "lastUpdated should be different", !p1.lastUpdated.equals(p2.lastUpdated)
        assertTrue "p2.lastUpdated should be after p1.lastUpdated", p1.lastUpdated.before(p2.lastUpdated)

        p2.delete()
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

        p.save()

        assertNotNull "should have saved a project", p
        assertEquals "should have saved project with id = '${id}'", p.id, id

        p.delete()
    }

    void testBulkSave() {
        def bulkDocuments = []

        Date firstTaskStartDate
        Date lastTaskStartDate

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

            if (!firstTaskStartDate) {
                firstTaskStartDate = t.startDate
            }
            lastTaskStartDate = t.startDate

            bulkDocuments << t
        }

        // bulk save all of the documents
        List<DocumentInfo> result = Project.bulkSave(bulkDocuments)

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
        result = Task.findByViewAndKeys("openByStartDate", [firstTaskStartDate, lastTaskStartDate])
        assertTrue "should have found at least 2 (depends upon timing) tasks", result.size() >= 2
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

        result = Task.list()
        result.each {info ->
            println info
        }
        assertEquals "should have found 20 tasks", 20, result.size()

        result = Task.list([max:10])
        assertEquals "should have found 10 tasks", 10, result.size()

        result = Task.findByView("openByName")
        assertEquals "should have found 20 open tasks", 20, result.size()

        result = Task.findByView("openByName", ["offset": 5, "max": 10])
        assertEquals "should have found 10 open tasks", 10, result.size()

        result = Task.findByView("openByName", ['startkey': "task-1", 'endkey': "task-10"])
        assertEquals "should have found 2 open tasks", 2, result.size()
        result.each {info ->
            println info
        }

        def descending = Task.findByView("openByName", ['startkey': "task-10", 'endkey': "task-1", "order": "desc"])
        assertEquals "should have found 2 open tasks", descending.size(), 2
        assertEquals "should be in reverse order", result[0].id, descending[1].id
        assertEquals "should be in reverse order", result[1].id, descending[0].id

        result = Task.findByViewAndKeys("openByName", ["task-15"])
        assertEquals "should have found 1 open task", 1, result.size()
        result.each {info ->
            println info
            assertEquals "should have found task #15", "task-15", info.key
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

    void testComplexObject() {
        def c = new Contact()

        c.name = "Tom Jones"
        c.company = "Acme, Corp."
        c.address.street1 = "100 Hollywood Blvd."
        c.address.city = "Los Angeles"
        c.address.state = "CA"
        c.save()

        assertNotNull "should have saved new contact", c
        assertNotNull "should have retrieved id of new contact", c.id
        assertNull "gender should be null", c.gender

        c.gender = Gender.MALE
        c.save()

        def c2 = Contact.get(c.id)
        assertEquals "contact should be male", c2.gender, Gender.MALE

        c.delete()
    }

    void testAttachments() {
        def id = "gorm-couchdb-att"

        // get (or create) our test project
        def p = Project.get(id)
        if (!p) {
            p = new Project()

            p.id = id
        }

        p.name = "A New Test Project w/attachments"
        p.startDate = new Date()
        p.save()

        def att = new File("grails-app/conf/DataSource.groovy")
        p.saveAttachment(att.path, "text", att.newInputStream(), att.length())

        p = Project.get(id)
        assertEquals "should have one attachment", 1, p.attachments.size()

        def att2 = new File("grails-app/conf/UrlMappings.groovy")
        p.saveAttachment(att2.path, "text", att2.newInputStream(), att2.length())

        p = Project.get(id)
        assertEquals "should have two attachments", 2, p.attachments.size()
        assertNotNull "attachment name should be ${att.path}", p.attachments[att.path]
        assertNotNull "attachment name should be ${att2.path}", p.attachments[att2.path]

        p.readAttachment(att.path)
        p.readAttachment(att2.path)

        p.deleteAttachment(att.path)

        p = Project.get(id)
        assertEquals "should have one attachmens", 1, p.attachments.size()

        p.delete()
    }
}