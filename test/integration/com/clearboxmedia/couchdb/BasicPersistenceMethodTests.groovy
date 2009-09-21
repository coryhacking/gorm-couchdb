package com.clearboxmedia.couchdb

import org.acme.Project


public class BasicPersistenceMethodTests extends GroovyTestCase {
    static transactional = false
    
    void testSaveAndGet() {

          def p = new Project(name:"InConcert")
          p.save()


          p = Project.get(p.id)

          assertNotNull "should have retrieved a project", p

          // test get(..) with a string for type conversion
          p = Project.get(p.id.toString())

          assertNotNull "should have retrieved a project", p
    }

}