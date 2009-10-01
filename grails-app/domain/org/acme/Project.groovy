package org.acme

import com.clearboxmedia.couchdb.CouchDBEntity
import com.clearboxmedia.couchdb.CouchDBId
import com.clearboxmedia.couchdb.CouchDBRev
import com.clearboxmedia.couchdb.CouchDBType

@CouchDBEntity
class Project {

    @CouchDBId
    String id

    @CouchDBRev
    String rev

    @CouchDBType
    static String type = 'project'

    String name
    Date startDate
    Date lastUpdated
    String frequency

    static constraints = {
        name blank: false
        id nullable: true
        rev nullable: true
        startDate nullable: true
        frequency nullable: true
    }
}
