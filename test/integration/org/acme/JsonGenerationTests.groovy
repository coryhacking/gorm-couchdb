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

/**
 *
 * @author Cory Hacking
 */
class JsonGenerationTests extends GroovyTestCase {

    void testContact() {

        // predefined json string for a contact
        String json = "{\"address\":{\"city\":\"Los Angeles\",\"state\":\"CA\",\"street1\":\"100 Hollywood Blvd.\",\"street2\":null,\"zip\":null},\"company\":\"Acme, Corp.\",\"gender\":\"MALE\",\"_id\":\"26b5811b3701c30c75d11f9a412103fa\",\"name\":\"Tom Jones\",\"type\":\"contact\",\"_rev\":\"2-ba19afa3cf78e7350202cf0c095c9aa4\"}"

        def contact = new Contact()

        // made up id and version
        contact.id = "26b5811b3701c30c75d11f9a412103fa"
        contact.version = "2-ba19afa3cf78e7350202cf0c095c9aa4"

        // fill in the rest of the data
        contact.name = "Tom Jones"
        contact.company = "Acme, Corp."
        contact.address.street1 = "100 Hollywood Blvd."
        contact.address.city = "Los Angeles"
        contact.address.state = "CA"
        contact.gender = Gender.MALE

        assertEquals "contact toJSON() should have returned the predefined string", json, contact.toJSON()
    }

    void testProject() {

        // predefined json string for a project
        String json = "{\"_attachments\":{},\"dateCreated\":null,\"frequency\":\"frequency\",\"_id\":\"26b5811b3701c30c75d11f9a412103fa\",\"lastUpdated\":null,\"name\":\"test project\",\"startDate\":\"3909\\/12\\/01 16:15:30 +0000\",\"type\":\"project\",\"_rev\":\"2-ba19afa3cf78e7350202cf0c095c9aa4\"}"

        def project = new Project()

        // made up id and version
        project.id = "26b5811b3701c30c75d11f9a412103fa"
        project.version = "2-ba19afa3cf78e7350202cf0c095c9aa4"

        project.name = "test project"
        project.startDate = new Date(2009, 10, 31, 9, 15, 30)
        project.frequency = "frequency"

        assertEquals "project toJSON() should have returned the predefined string", json, project.toJSON()
    }

    void testTask() {

        // predefined json string for a task
        String json = "{\"actualHours\":null,\"completionDate\":null,\"dateCreated\":null,\"description\":\"This is the description for the task.\",\"estimatedHours\":5,\"lastUpdated\":null,\"meta\":\"project-task\",\"name\":\"test task\",\"projectId\":\"project-id\",\"startDate\":\"3909\\/12\\/01 16:15:30 +0000\",\"_id\":\"26b5811b3701c30c75d11f9a412103fa\",\"_rev\":\"2-ba19afa3cf78e7350202cf0c095c9aa4\"}"

        def task = new Task()

        // made up id and version
        task.taskId = "26b5811b3701c30c75d11f9a412103fa"
        task.taskVersion = "2-ba19afa3cf78e7350202cf0c095c9aa4"

        task.name = "test task"
        task.projectId = "project-id"
        task.startDate = new Date(2009, 10, 31, 9, 15, 30)
        task.description = "This is the description for the task."
        task.estimatedHours = 5

        assertEquals "task toJSON() should have returned the predefined string", json, task.toJSON()
    }
}
