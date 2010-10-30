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

/**
 * @author Cory Hacking
 */
public class ValidationTests extends GroovyTestCase {

    void testDefaultConstraints() {
        def p = new Project()

		p.validate()
		assertFalse "should not have errors", p.hasErrors()

		p.name = ""

		p.validate()
		assertTrue "should have errors (can't be blank)", p.hasErrors()
		assertNull "should not have saved", p.save()
        assertEquals "should have 1 error", p.errors.allErrors.size(), 1
        assertEquals "name should be in error", p.errors.allErrors[0].field, "name"
    }

	void testNamedConstraints() {

		def p = new Project(name: "validation test")

		p.validate()
		assertFalse "should not have errors", p.hasErrors()

		p.description = ""

		p.validate()
		assertTrue "should have errors (can't be blank)", p.hasErrors()

		p.description = "too short"

		p.validate()
		assertTrue "should have errors (too short)", p.hasErrors()

		p.description = "long enough"
		p.validate()
		assertFalse "should not have errors", p.hasErrors()
	}
}