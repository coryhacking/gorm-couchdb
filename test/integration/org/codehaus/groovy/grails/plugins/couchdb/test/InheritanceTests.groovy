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
}
