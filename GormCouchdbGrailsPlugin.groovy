import com.clearboxmedia.couchdb.*

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
