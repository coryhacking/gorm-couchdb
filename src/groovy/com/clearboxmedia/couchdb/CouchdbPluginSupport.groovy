package com.clearboxmedia.couchdb

import org.springframework.context.ApplicationContext
import org.codehaus.groovy.grails.commons.GrailsDomainClass


public class CouchdbPluginSupport {

    static couchdbProps = [:]
    static couchdbConfigClass

    static doWithApplicationContext = { ApplicationContext applicationContext ->
         for(GrailsDomainClass dc in application.domainClasses) {
             println("domain class is : " + dc)
             def clazz = dc.clazz
             println ("clazz is ${clazz}")
             if(clazz.isAnnotationPresent(CouchDBEntity)) {
                 println("domain class: ${dc.name} is a couchdb class")
             } else {
                 println("domain class: ${dc.name} is a regular domain class")
             }
         }
        
    }


}