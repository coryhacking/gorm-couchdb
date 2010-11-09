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
import org.jcouchdb.document.DesignDocument
import org.jcouchdb.document.ValueRow
import org.jcouchdb.document.View
import org.jcouchdb.exception.DataAccessException

/**
 * Tests that we can get and save CouchDB design documents 
 *
 * @author Cory Hacking
 */
class DesignDocumentTests extends GroovyTestCase {

    protected void setUp() {
        super.setUp()

        // delete the 'contact' design document (if it exists)
        def design = Contact.getDesignDocument()
        if (design) {
            Contact.deleteDesignDocument(design)
        }

    }

    protected void tearDown() {
        super.tearDown()
    }

    void testCreateDesignDocument() {

        try {
            Contact.list()
            fail "Contact.list() should have failed because there is no 'list' view"
        } catch (Exception e) {
            assertTrue "should have thrown a DataAccessException", e instanceof DataAccessException
        }

        // start a new one
        def design = new DesignDocument();

        // add a temporary "open" view
        design.addView("list", new View("function(doc) { if (doc.type == 'person.contact') { emit(doc.name, {name:doc.name, company:doc.company}); }}"))

        // save the design document
        Contact.saveDesignDocument(design)

        // create a new contact
        createContact()

        // make sure we have at least one contact
        def results = Contact.list()
        assertTrue "should have returned at least one contact", results.size() > 0

        // loop through and delete the contacts returned
        results.each {ValueRow row ->
            def c = Contact.get(row.id)
            if (c) {
                c.delete()
            }
        }

        // delete the design document
        Contact.deleteDesignDocument(design)
    }

    private Contact createContact() {
        def contact = new Contact()

        contact.name = "Tom Jones"
        contact.company = "Acme, Corp."
        contact.address.street1 = "100 Hollywood Blvd."
        contact.address.city = "Los Angeles"
        contact.address.state = "CA"
        contact.gender = Gender.MALE

        contact.save()
    }
}
