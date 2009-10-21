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

import grails.plugins.couchdb.CouchEntity
import grails.plugins.couchdb.CouchId
import grails.plugins.couchdb.CouchVersion

/**
 *
 * @author Warner Onstine, Cory Hacking
 */
@CouchEntity(type = "project-task")
class Task {

    @CouchId
    String taskId

    @CouchVersion
    String taskVersion

    String projectId
    String name

    Date startDate
    Date completionDate
    Integer estimatedHours
    Integer actualHours

    String description

    Date dateCreated
    Date lastUpdated

    static constraints = {
        projectId blank: false
        name blank: false
    }
}
