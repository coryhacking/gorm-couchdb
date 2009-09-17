package org.acme

import com.clearboxmedia.couchdb.CouchDBEntity
import com.clearboxmedia.couchdb.CouchDBId
import com.clearboxmedia.couchdb.CouchDBRev

@CouchDBEntity
class Project {

    @CouchDBId
    String id
    
    @CouchDBRev
    String rev

    String name
    String startDate
    String frequency
}
