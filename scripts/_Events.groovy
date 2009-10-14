/*
 * Copyright 2004-2005 the original author or authors.
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

/**
 *
 * @author Warner Onstine, Cory Hacking
 */
eventCreateWarStart = {warLocation, stagingDir ->

    // Remove the couchdb views from the web-inf/classes folder
    ant.delete(dir: "${stagingDir}/WEB-INF/classes/couchdb/views", failonerror: true)

    // create the couchdb resource folder
    def viewsDir = "${stagingDir}/WEB-INF/grails-app/couchdb/views"
    ant.mkdir(dir: viewsDir)

    // copy everything over
    ant.copy(todir: viewsDir) {
        fileset(dir: "${basedir}/grails-app/conf/couchdb/views", includes: "**/**")
    }

}
            