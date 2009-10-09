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
import com.clearboxmedia.couchdb.*

/**
 *
 * @author Warner Onstine, Cory Hacking
 */
class GormCouchdbGrailsPlugin {

    // the plugin version
    def version = "0.1"

    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "1.1 > *"

    // the other plugins this plugin depends on
    def dependsOn = [core : '1.1 > *',
                     domainClass : '1.0 > *']

    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            "grails-app/domain/org/acme/*.groovy",
            "grails-app/views/error.gsp"
    ]

    // TODO Fill in these fields
    def author = "Warner Onstine"
    def authorEmail = ""
    def title = "GORM-CouchDB plugin"
    def description = '''\\
A plugin that emulates the behavior of the GORM-Hibernate plugin against a CouchDB-0.9.x NoSQL database
'''

    // URL to the plugin's documentation
    def documentation = "http://grails.org/GormCouchdb+Plugin"

    def doWithDynamicMethods = CouchdbPluginSupport.doWithDynamicMethods

}
