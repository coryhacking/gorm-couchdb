package org.acme

import com.clearboxmedia.couchdb.CouchEntity
import com.clearboxmedia.couchdb.CouchId
import com.clearboxmedia.couchdb.CouchRev

@CouchEntity(type = "project-task")
class Task {

    @CouchId
    String taskId

    @CouchRev
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
