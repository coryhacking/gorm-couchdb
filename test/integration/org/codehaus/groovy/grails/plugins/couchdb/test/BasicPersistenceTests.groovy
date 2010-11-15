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

import org.acme.Contact
import org.acme.Gender
import org.acme.Project
import org.acme.Task
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.springframework.mock.web.MockHttpServletRequest

/**
 * @author Warner Onstine, Cory Hacking
 */
public class BasicPersistenceTests extends GroovyTestCase {

	void testSaveAndGet() {

		def p1 = new Project(name: "InConcert")

		p1.startDate = new Date()
		p1.pass = "transient test"

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
		assertTrue "the project field 'pass' is transient and should not have been saved", p1.pass != p2.pass

		Thread.currentThread().sleep(1000);

		p2.save()
		assertTrue "lastUpdated should be different", !p1.lastUpdated.equals(p2.lastUpdated)
		assertTrue "p2.lastUpdated should be after p1.lastUpdated", p1.lastUpdated.before(p2.lastUpdated)

		def t1 = new Task()
		t1.taskId = "${p2.id}-task"
		t1.name = "task"
		t1.projectId = p2.id
		t1.startDate = new Date()
		t1.description = "This is the description."
		t1.estimatedHours = 5
		t1.pass = "transient test"

		t1.save()

		def t2 = Task.get(t1.taskId)
		assertNotNull "should have retrieved a task", t2
		assertTrue "the task field 'pass' is transient and should not have been saved", t1.pass != t2.pass

		p2.delete()
		t2.delete()

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

	void testParameterMapToProperties() {

		MockHttpServletRequest mockRequest = new MockHttpServletRequest()

		mockRequest.addParameter "name", "Tom Jones"
		mockRequest.addParameter "company", "Acme, Corp."
		mockRequest.addParameter "address.street1", "100 Hollywood Blvd."
		mockRequest.addParameter "address.city", "Los Angeles"
		mockRequest.addParameter "address.state", "CA"

		def params = new GrailsParameterMap(mockRequest)

		def c = new Contact()

		// assign all of the properties
		c.properties = params

		assertEquals "Name is Tom Jones", c.name, "Tom Jones"
		assertEquals "company is Acme, Corp", c.company, "Acme, Corp."
		assertNotNull "Address is not null", c.address
		assertEquals "Street is 100 Hollywood Blvd", c.address.street1, "100 Hollywood Blvd."
		assertEquals "City is Los Angeles", c.address.city, "Los Angeles"
		assertEquals "State is CA", c.address.state, "CA"

		c.save()

		assertNotNull "Should have saved contact", c.id

		c.delete()
	}
}