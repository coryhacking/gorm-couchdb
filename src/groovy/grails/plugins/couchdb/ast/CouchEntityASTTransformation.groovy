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
import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.PropertyNode
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.GStringExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

/**
 *
 * @author Cory Hacking
 */
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class CouchEntityASTTransformation implements ASTTransformation {

    private static final Log log = LogFactory.getLog(CouchEntityASTTransformation.class)

    private static final String IDENTITY = GrailsDomainClassProperty.IDENTITY
    private static final String VERSION = GrailsDomainClassProperty.VERSION

    private static final ClassNode COUCH_ENTITY = new ClassNode(CouchEntity)

    private static final ClassNode COUCH_ID = new ClassNode(CouchId)
    private static final ClassNode COUCH_VERSION = new ClassNode(CouchVersion)

    private static final ClassNode PERSISTENCE_ID = new ClassNode(Id)
    private static final ClassNode PERSISTENCE_VERSION = new ClassNode(Version)

    public void visit(ASTNode[] nodes, SourceUnit sourceUnit) {
        if (nodes.length != 2 || !(nodes[0] instanceof AnnotationNode) || !(nodes[1] instanceof AnnotatedNode)) {
            throw new RuntimeException("Internal error: expecting [AnnotationNode, AnnotatedNode] but got: " + Arrays.asList(nodes))
        }

        AnnotationNode node = (AnnotationNode) nodes[0]
        ClassNode owner = (ClassNode) nodes[1]

        injectIdProperty(owner)
        injectVersionProperty(owner)
    }

    private void injectIdProperty(ClassNode classNode) {
        Collection<FieldNode> nodes = classNode.fields.findAll {FieldNode fieldNode -> fieldNode.getAnnotations(COUCH_ID) || fieldNode.getAnnotations(PERSISTENCE_ID) }
        PropertyNode identity = getProperty(classNode, IDENTITY)

        if (nodes) {
            // look to see if the identity field was injected and not one of our annotated nodes
            if (identity && identity.field.lineNumber < 0 && !nodes.findAll {FieldNode fieldNode -> fieldNode.name == identity.name}) {

                // change the injected toString() method to use the proper id field
                fixupToStringMethod(classNode, nodes[0])

                // remove the old identifier
                removeProperty(classNode, identity.name)
            }
        } else {

            // if we don't have an annotated id then look for a plain id field
            if (identity) {
                if (identity.type.typeClass != String.class && identity.field.lineNumber < 0) {
                    if (log.isDebugEnabled()) {
                        log.debug("Changing the type of property [" + IDENTITY + "] of class [" + classNode.getName() + "] to String.")
                    }
                    identity.field.type = new ClassNode(String.class)
                }
                return
            }

            if (log.isDebugEnabled()) {
                log.debug("Adding property [" + IDENTITY + "] to class [" + classNode.getName() + "]")
            }
            classNode.addProperty(IDENTITY, Modifier.PUBLIC, new ClassNode(String.class), null, null, null)
        }
    }

    private void fixupToStringMethod(ClassNode classNode, FieldNode idNode) {

        MethodNode method = classNode.getDeclaredMethod("toString", [] as Parameter[]);
        if (method != null && method.lineNumber < 0 && (method.isPublic() || method.isProtected()) && !method.isAbstract()) {
            GStringExpression ge = new GStringExpression(classNode.getName() + ' : ${' + idNode.name + '}');
            ge.addString(new ConstantExpression(classNode.getName() + " : "));
            ge.addValue(new VariableExpression(idNode.name));

            method.variableScope.removeReferencedClassVariable("id")
            method.variableScope.putReferencedClassVariable(idNode)

            method.code = new ReturnStatement(ge);

            if (log.isDebugEnabled()) {
                log.debug("Changing method [toString()] on class [" + classNode.getName() + "] to use id field [" + id + "]");
            }
        }
    }

    private void injectVersionProperty(ClassNode classNode) {
        Collection<FieldNode> nodes = classNode.fields.findAll {FieldNode fieldNode -> fieldNode.getAnnotations(COUCH_VERSION) || fieldNode.getAnnotations(PERSISTENCE_VERSION) }
        PropertyNode version = getProperty(classNode, VERSION)

        if (nodes) {
            // look to see if the version field was injected and not one of our annotated nodes
            if (version && version.field.lineNumber < 0 && !nodes.findAll {FieldNode fieldNode -> fieldNode.name == version.name}) {
                removeProperty(classNode, version.name)
            }

        } else {

            // if we don't have an annotated id then look for a plain version field
            if (version) {
                if (version.type.typeClass != String.class && version.field.lineNumber < 0) {
                    if (log.isDebugEnabled()) {
                        log.debug("Changing the type of property [" + VERSION + "] of class [" + classNode.getName() + "] to String.")
                    }
                    version.field.type = new ClassNode(String.class)
                }
                return
            }

            if (log.isDebugEnabled()) {
                log.debug("Adding property [" + VERSION + "] to class [" + classNode.getName() + "]")
            }
            classNode.addProperty(VERSION, Modifier.PUBLIC, new ClassNode(String.class), null, null, null)
        }
    }

    private PropertyNode getProperty(ClassNode classNode, String propertyName) {
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

    private removeProperty(ClassNode classNode, String propertyName) {
        if (log.isDebugEnabled()) {
            log.debug("Removing property [" + propertyName + "] from class [" + classNode.getName() + "]")
        }

        // remove the property from the fields and properties arrays
        for (int i = 0; i < classNode.fields.size(); i++) {
            if (classNode.fields[i].name == propertyName) {
                classNode.fields.remove(i)
                break
            }
        }
        for (int i = 0; i < classNode.properties.size(); i++) {
            if (classNode.properties[i].name == propertyName) {
                classNode.properties.remove(i)
                break
            }
        }

        // this doesn't seem to be necessary (and is only technically possible
        // because groovy ignores private scope), but we're going to try to be thorough.
        classNode.getFieldIndexLazy().remove(propertyName)
    }
}
