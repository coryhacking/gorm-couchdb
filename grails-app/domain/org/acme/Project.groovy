package org.acme

import com.clearboxmedia.couchdb.CouchDBEntity
import com.clearboxmedia.couchdb.CouchDBId
import com.clearboxmedia.couchdb.CouchDBRev

@CouchDBEntity
class Project {

    @CouchDBId
    String id

    @CouchDBRev
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
