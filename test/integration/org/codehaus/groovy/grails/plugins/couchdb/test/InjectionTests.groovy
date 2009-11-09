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

/**
 * Tests that ensure our ASTTransformations are working correctly
 *
 * @author Cory Hacking
 */
class InjectionTests extends GroovyTestCase {

    /**
     * Test the basic Id and Version injections occurred.  This includes removing the id and version fields
     * that the standard hibernate plugin injects and rewriting toString().
     */
    void testIdAndVersionInjection() {
        def id = "gorm-test-id"
        def version = "1-22ba1c6f98a7a0981fe4b716a1153e16"

        // test that the id & version WERE injected into the Project class
        def p = new Project()
        p.id = id
        p.version = version

        assertTrue "Project id should be a string", p.id instanceof String
        assertTrue "Project version should be a string", p.version instanceof String

        assertEquals "Project id should be ${id}", id, p.id
        assertEquals "Project version should be ${version}", version, p.version

        assertEquals "project type should return 'project'", "project", p.type

        // test that the id & version were not injected in (or were removed from) the Task class
        def t = new Task()
        try {
            t.id = id
            fail "the injected id field should have been removed from the Task class"
        } catch (Exception e) {
            assertTrue "should have thrown a MissingPropertyException", e instanceof MissingPropertyException
        }

        try {
            t.version = version
            fail "the injected version field should have been removed from the Task class"
        } catch (Exception e) {
            assertTrue "should have thrown a MissingPropertyException", e instanceof MissingPropertyException
        }

        t.taskId = "gorm-couchdb"
        assertTrue "toString() should return the class name and taskId", (t.class.getName() + " : ${t.taskId}") == t.toString()

        assertEquals "task meta should return the 'project-task", "project-task", t.meta
    }

    /**
     * Test that our jcouchdb / svenson annotations were injected.
     */
    void testAnnotationInjection() {

    }
}
