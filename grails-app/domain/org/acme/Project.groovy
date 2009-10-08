package org.acme

import com.clearboxmedia.couchdb.CouchEntity
import com.clearboxmedia.couchdb.CouchId
import com.clearboxmedia.couchdb.CouchRev

@CouchEntity
class Project {

    @CouchId
    String id

    @CouchRev
    String version

    String name
    Date startDate
    Date lastUpdated
    String frequency

    static constraints = {
        id nullable: true
        version nullable: true
        name blank: false
        startDate nullable: true
        frequency nullable: true
    }
}
