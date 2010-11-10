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
import org.acme.Person

/**
 *
 * @author Cory Hacking
 */
class InheritanceTests extends GroovyTestCase {

	void testDefaultType() {

		def p = new Person()
		assertEquals "should be using the default type for person", 'person', p.type

		def c = new Contact()
		assertEquals "should be using the default type for contact", 'person.contact', c.type

	}

	void testSubType() {

		def c = new Contact()

		c.name = "Tom Jones"
		c.company = "Acme, Corp."
		c.address.street1 = "100 Hollywood Blvd."
		c.address.city = "Los Angeles"
		c.address.state = "CA"
		c.save()

		assertNotNull "should have saved new contact", c
		assertNotNull "should have retrieved id of new contact", c.id

		// re-read the contact
		c = Contact.get(c.id)

		def p = Person.get(c.id)
		assertTrue "should have read person as contact subclass", p instanceof Contact
		assertEquals "should have read the same id's", c.id, p.id
		assertEquals "should have read the same version's", c.version, p.version

		def result = Person.list(include_docs: true)
		assertEquals "should have found 1 person", 1, result.size()
		assertNotNull "results should include document", result[0].document
		assertTrue "results document should be a contact", result[0].document instanceof Contact

		// create another person
		p = new Person()
		p.name = "Zelda Wurzelbacher"
		p.save()

		// rerun the query
		result = Person.list(include_docs: true)
		assertEquals "should have found 2 people", 2, result.size()
		assertNotNull "result[0] should include document", result[0].document
		assertNotNull "result[1] should include document", result[1].document
		assertTrue "result[0] document should be a contact", result[0].document instanceof Contact
		assertTrue "result[1] document should be a person", result[1].document instanceof Person

		// delete both documents
		result.each {it ->
			it.document.delete()
		}

		// make sure everything was deleted
		result = Person.list()
		assertEquals "should have found 0 people", 0, result.size()
	}
}
