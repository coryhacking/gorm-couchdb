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

import org.codehaus.groovy.grails.plugins.couchdb.CouchdbPluginSupport
import org.codehaus.groovy.grails.plugins.couchdb.domain.CouchDomainClassArtefactHandler
import org.springframework.core.io.Resource

/**
 *
 * @author Warner Onstine, Cory Hacking
 */
class GormCouchdbGrailsPlugin {

    // the plugin version
    def version = '0.7.1'

    // the version or versions of Grails the plugin is designed for
    def grailsVersion = '1.2 > *'

    // the other plugins this plugin depends on
    def dependsOn = [core: '1.2 > *',
             hibernate: '1.2 > *'
    ]

    def loadAfter = ['core', 'domainClass', 'hibernate']

    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            'grails-app/conf/spring/**',
            'grails-app/conf/hibernate/**',
            'grails-app/controllers/**',
            'grails-app/domain/**',
            'grails-app/views/**',
            'grails-app/i18n/**',
            'web-app/**',

            'grails-app/conf/couchdb/views/**',
            'src/groovy/org/acme/**',
            'src/java/org/acme/**'
    ]

    def artefacts = [CouchDomainClassArtefactHandler]

    def watchedResources = [
            'file:./grails-app/conf/couchdb/views/**',
    ]

    def author = 'Warner Onstine, Cory Hacking'
    def authorEmail = ''
    def title = 'Grails CouchDB Plugin'
    def description = "A plugin that emulates the behavior of the GORM-Hibernate plugin against a CouchDB document-oriented database"

    // URL to the plugin's documentation
    def documentation = 'http://grails.org/GormCouchdb+Plugin'

    def doWithSpring = CouchdbPluginSupport.doWithSpring

    def doWithDynamicMethods = CouchdbPluginSupport.doWithDynamicMethods

    def onChange = {event ->

        if (event.source instanceof Resource) {
            log.debug("CouchDB view ${event.source} changed. Updating views...")

            // update the couch views...
            //  todo: update just the specific view
            CouchdbPluginSupport.updateCouchViews(application)

        }
    }
}
