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

/**
 *
 * @author Cory Hacking
 */
class AttachmentsTests extends GroovyTestCase {

    void testAttachments() {
        def id = "gorm-couchdb-att"

        // create a new project
        def p = Project.get(id)
        if (p) {
            p.delete()
        }        

        p = new Project()

        p.id = id
        p.name = "A New Test Project w/attachments"
        p.startDate = new Date()
        p.save()

        def att = new File("grails-app/conf/DataSource.groovy")
        p = p.saveAttachment(att.path, "text/plain", att.newInputStream(), att.length())

        assertEquals "should have one attachment", 1, p.attachments.size()
        assertEquals "contentType should be 'text/plain'", 'text/plain', p.attachments[att.path].contentType
        assertEquals "length should be '${att.length()}", att.length(), p.attachments[att.path].length

        def att2 = new File("grails-app/conf/Config.groovy")
		def att3 = new File("grails-app/conf/UrlMappings.groovy")
        p = p.saveAttachment(att2.path, "text/plain", att2.newInputStream(), att2.length())
		p = p.saveAttachment(att3.path, "text/plain", att3.newInputStream(), att3.length())

        assertEquals "should have three attachments", 3, p.attachments.size()
        assertNotNull "attachment name should be ${att.path}", p.attachments[att.path]
        assertNotNull "attachment name should be ${att2.path}", p.attachments[att2.path]

        p.readAttachment(att.path)
        p.readAttachment(att2.path)

        p = p.deleteAttachment(att.path)

        assertEquals "should have two attachmens", 2, p.attachments.size()

        p.delete()
    }
}
