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
import com.clearboxmedia.couchdb.domain.CouchDomainClassArtefactHandler
import org.springframework.core.io.Resource
import org.springframework.beans.factory.config.MethodInvokingFactoryBean
import org.codehaus.groovy.grails.validation.GrailsDomainClassValidator

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
    def dependsOn = [core: '1.1 > *']

    def loadAfter = ['core']

    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            "grails-app/conf/spring/**",
            "grails-app/conf/hibernate/**",
            "grails-app/i18n/**",
            "grails-app/domain/**",
            "grails-app/views/**",
            "grails-app/conf/couchdb/views/**",
            "grails-app/couchdb/**",
            "src/groovy/org/acme/**",
            "src/java/org/acme/**"
    ]

    def artefacts = [CouchDomainClassArtefactHandler]

    def watchedResources = [
            "file:./grails-app/conf/couchdb/views/**",
            "file:./grails-app/couchdb/**"
    ]

    def author = "Warner Onstine, Cory Hacking"
    def authorEmail = ""
    def title = "Grails CouchDB Plugin"
    def description = '''\\
A plugin that emulates the behavior of the GORM-Hibernate plugin against a CouchDB document-oriented database
'''

    // URL to the plugin's documentation
    def documentation = "http://grails.org/GormCouchdb+Plugin"

    def doWithSpring = CouchdbPluginSupport.doWithSpring

    def doWithDynamicMethods = CouchdbPluginSupport.doWithDynamicMethods

    def onChange = {event ->

        if (event.source instanceof Resource) {
            log.debug("CouchDB view ${event.source} changed. Updating views...")

            // update the couch views...
            //  todo: update just the specific view
            CouchdbPluginSupport.updateCouchViews(application)

        } else if (application.isArtefactOfType(CouchDomainClassArtefactHandler.TYPE, event.source)) {
            log.debug("CouchDomain class ${event.source} changed. Reloading...")

            def context = event.ctx
            if (!context) {
                log.warn("Application context not found. Can't reload.")
                return
            }

            def dc = application.addArtefact(CouchDomainClassArtefactHandler.TYPE, event.source)

            def beans = beans {
                "${dc.fullName}"(dc.getClazz()) {bean ->
                    bean.singleton = false
                    bean.autowire = "byName"
                }

                "${dc.fullName}CouchDomainClass"(MethodInvokingFactoryBean) {
                    targetObject = ref("grailsApplication", true)
                    targetMethod = "getArtefact"
                    arguments = [CouchDomainClassArtefactHandler.TYPE, dc.fullName]
                }

                "${dc.fullName}PersistentClass"(MethodInvokingFactoryBean) {
                    targetObject = ref("${dc.fullName}CouchDomainClass")
                    targetMethod = "getClazz"
                }

                "${dc.fullName}Validator"(GrailsDomainClassValidator) {
                    messageSource = ref("messageSource")
                    domainClass = ref("${dc.fullName}CouchDomainClass")
                    grailsApplication = ref("grailsApplication", true)
                }
            }

            context.registerBeanDefinition("${dc.fullName}", beans.getBeanDefinition("${dc.fullName}"))
            context.registerBeanDefinition("${dc.fullName}CouchDomainClass", beans.getBeanDefinition("${dc.fullName}CouchDomainClass"))
            context.registerBeanDefinition("${dc.fullName}PersistentClass", beans.getBeanDefinition("${dc.fullName}PersistentClass"))
            context.registerBeanDefinition("${dc.fullName}Validator", beans.getBeanDefinition("${dc.fullName}Validator"))

            // add the dynamic methods back to the class
            CouchdbPluginSupport.doWithDynamicMethods(event.ctx)
        }
    }

    // We may want to just reload the entire context (the way the domain plugin works) instead of just our couch documents.
    // For now, however, we will reload the controllers and services and see how that goes.
    def influences = ['controllers', 'services']

}
