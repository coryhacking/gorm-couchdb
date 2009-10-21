/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package grails.plugins.couchdb.ast

import grails.plugins.couchdb.CouchEntity
import grails.plugins.couchdb.CouchId
import grails.plugins.couchdb.CouchVersion
import java.lang.reflect.Modifier
import javax.persistence.Id
import javax.persistence.Version
import org.apache.commons.lang.StringUtils
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.PropertyNode
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

/**
 *
 * @author Cory Hacking
 */
@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
class CouchEntityASTTransformation implements ASTTransformation {

    private static final Log log = LogFactory.getLog(CouchEntityASTTransformation.class)

    private static final ClassNode COUCH_ENTITY = new ClassNode(CouchEntity)

    private static final ClassNode COUCH_ID = new ClassNode(CouchId)
    private static final ClassNode COUCH_VERSION = new ClassNode(CouchVersion)

    private static final ClassNode PERSISTENCE_ID = new ClassNode(Id)
    private static final ClassNode PERSISTENCE_VERSION = new ClassNode(Version)

    public void visit(ASTNode[] nodes, SourceUnit sourceUnit) {
        def couchEntityClasses = sourceUnit.getAST().getClasses().findAll {ClassNode classNode ->
            classNode.getAnnotations(COUCH_ENTITY)
        }

        // make sure that an id and version property is set on each of our CouchEntity classes 
        couchEntityClasses.each {ClassNode classNode ->

            if (log.isDebugEnabled()) {
                log.debug("[CouchEntityASTTransformation] scanning class [" + classNode.getName() + "]")
            }

            injectIdProperty classNode
            injectVersionProperty classNode

        }
    }

    private void injectIdProperty(ClassNode classNode) {
        final boolean hasAnnotatedId = classNode.fields.findAll {FieldNode fieldNode -> fieldNode.getAnnotations(COUCH_ID) || fieldNode.getAnnotations(PERSISTENCE_ID) }.size() > 0
        if (!hasAnnotatedId) {

            // if we don't have an annotated id then look for a plain id field
            final PropertyNode node = getDomainProperty(classNode, GrailsDomainClassProperty.IDENTITY)
            if (node) {
                if (node.type.typeClass != String.class && node.field.lineNumber < 0) {
                    node.field.type = new ClassNode(String.class)
                }
                return
            }

            if (log.isDebugEnabled()) {
                log.debug("[CouchEntityASTTransformation] Adding / modifying property [" + GrailsDomainClassProperty.IDENTITY + "] of class [" + classNode.getName() + "]")
            }

            classNode.addProperty(GrailsDomainClassProperty.IDENTITY, Modifier.PUBLIC, new ClassNode(String.class), null, null, null)
        }
    }

    private void injectVersionProperty(ClassNode classNode) {
        final boolean hasAnnotatedVersion = classNode.fields.findAll {FieldNode fieldNode -> fieldNode.getAnnotations(COUCH_VERSION) || fieldNode.getAnnotations(PERSISTENCE_VERSION) }.size() > 0
        if (!hasAnnotatedVersion) {

            // if we don't have an annotated id then look for a plain id field
            final PropertyNode node = getDomainProperty(classNode, GrailsDomainClassProperty.VERSION)
            if (node) {
                if (node.type.typeClass != String.class && node.field.lineNumber < 0) {
                    node.field.type = new ClassNode(String.class)
                }
                return
            }

            if (log.isDebugEnabled()) {
                log.debug("[CouchEntityASTTransformation] Adding / modifying property [" + GrailsDomainClassProperty.VERSION + "] of class [" + classNode.getName() + "]")
            }

            classNode.addProperty(GrailsDomainClassProperty.VERSION, Modifier.PUBLIC, new ClassNode(String.class), null, null, null)
        }
    }

    private PropertyNode getDomainProperty(ClassNode classNode, String propertyName) {
        if (classNode == null || StringUtils.isBlank(propertyName))
            return null

        // find the given class property
        // do we need to deal with parent classes???
        for (PropertyNode pn: classNode.properties) {
            if (pn.getName().equals(propertyName) && !pn.isPrivate()) {
                return pn
            }
        }

        return null
    }
}
