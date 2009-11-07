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

eventCompileStart = {target ->

    // make sure that our ast transformation and other classes get compiled first
    if (grailsAppName == "gorm-couchdb") {

        // don't need to do this more than once
        if (getBinding().variables.containsKey("_gorm_couchdb_compile_called")) return
            _gorm_couchdb_compile_called = true

        ant.sequential {
            echo "Compiling gorm-couchdb plugin..."
            
            path id: "grails.compile.classpath", compileClasspath
            mkdir dir: classesDirPath

            def classpathId = "grails.compile.classpath"

            groovyc(destdir: classesDirPath,
                    projectName: grailsSettings.baseDir.name,
                    classpathref: classpathId,
                    encoding: "UTF-8") {

                src(path: "${basedir}/src/groovy")
                src(path: "${basedir}/src/java")

                javac(classpathref: classpathId, debug: "yes")
            }
        }
    }
}

            