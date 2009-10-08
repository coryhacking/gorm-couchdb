package org.acme

import com.clearboxmedia.couchdb.CouchDBEntity
import com.clearboxmedia.couchdb.CouchDBId
import com.clearboxmedia.couchdb.CouchDBRev

@CouchDBEntity(type = "project-task")
class Task {

    @CouchDBId
    String taskId

    @CouchDBRev
    String taskVersion

    String projectId
    String name

    Date startDate
    Date completionDate
    Integer estimatedHours
    Integer actualHours

    String description

    static constraints = {
        projectId blank: false
        name blank: false
    }
}
