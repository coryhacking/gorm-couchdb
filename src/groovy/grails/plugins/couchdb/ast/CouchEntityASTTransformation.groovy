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

import grails.plugins.couchdb.CouchAttachments
import grails.plugins.couchdb.CouchEntity
import grails.plugins.couchdb.CouchId
import grails.plugins.couchdb.CouchVersion
import java.lang.reflect.Modifier
import javax.persistence.Id
import javax.persistence.Transient
import javax.persistence.Version
import org.apache.commons.lang.StringUtils
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.PropertyNode
import org.codehaus.groovy.ast.expr.BinaryExpression
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.FieldExpression
import org.codehaus.groovy.ast.expr.GStringExpression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.classgen.Verifier
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty
import org.codehaus.groovy.syntax.Token
import org.codehaus.groovy.syntax.Types
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.jcouchdb.document.Attachment
import org.svenson.JSONProperty
import org.svenson.JSONTypeHint
import org.svenson.converter.JSONConverter

/**
 *
 * @author Cory Hacking
 */
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class CouchEntityASTTransformation implements ASTTransformation {

    private static final Log log = LogFactory.getLog(CouchEntityASTTransformation.class)

    private static final String IDENTITY = GrailsDomainClassProperty.IDENTITY
    private static final String VERSION = GrailsDomainClassProperty.VERSION
    private static final String ATTACHMENTS = "attachments"

    private static final ClassNode COUCH_ENTITY = new ClassNode(CouchEntity)

    private static final ClassNode COUCH_ID = new ClassNode(CouchId)
    private static final ClassNode COUCH_VERSION = new ClassNode(CouchVersion)
    private static final ClassNode COUCH_ATTACHMENTS = new ClassNode(CouchAttachments)

    private static final ClassNode PERSISTENCE_ID = new ClassNode(Id)
    private static final ClassNode PERSISTENCE_VERSION = new ClassNode(Version)
    private static final ClassNode PERSISTENCE_TRANSIENT = new ClassNode(Transient)

    private static final ClassNode JSON_PROPERTY = new ClassNode(JSONProperty)
    private static final ClassNode JSON_TYPE_HINT = new ClassNode(JSONTypeHint)
    private static final ClassNode JSON_CONVERTER = new ClassNode(JSONConverter)

    private static final ClassNode STRING_TYPE = new ClassNode(String)
    private static final ClassNode BOOLEAN_TYPE = new ClassNode(boolean)


    public void visit(ASTNode[] nodes, SourceUnit sourceUnit) {
        if (nodes.length != 2 || !(nodes[0] instanceof AnnotationNode) || !(nodes[1] instanceof AnnotatedNode)) {
            throw new RuntimeException("Internal error: expecting [AnnotationNode, AnnotatedNode] but got: " + Arrays.asList(nodes))
        }

        AnnotationNode node = (AnnotationNode) nodes[0]
        ClassNode owner = (ClassNode) nodes[1]

        injectEntityType(owner, node)

        injectIdProperty(owner)
        injectVersionProperty(owner)
        injectAttachmentsProperty(owner)

        injectJSONPropertyAnnotations(owner)
    }

    private void injectEntityType(ClassNode classNode, AnnotationNode entity) {

        String typeValue = entity.members["type"]?.value ?: classNode.nameWithoutPackage.toLowerCase()
        String typeFieldName = entity.members["typeFieldName"]?.value ?: "type"

        // inject the type property if it doesn't already exist
        if (typeValue != "" && typeFieldName != "" && !getProperty(classNode, typeFieldName)) {

            String getterName = "get" + Verifier.capitalize(typeFieldName)
            MethodNode getter = classNode.getGetterMethod(getterName)
            if (getter == null) {
                getter = new MethodNode(getterName,
                        Modifier.PUBLIC,
                        STRING_TYPE,
                        Parameter.EMPTY_ARRAY,
                        null,
                        new ReturnStatement(
                                new ConstantExpression(typeValue)
                        ))

                setJsonPropertyAnnotation(getter, ["readOnly": ConstantExpression.TRUE])

                classNode.addMethod(getter)
            }
        }
    }

    private void injectIdProperty(ClassNode classNode) {
        Collection<FieldNode> nodes = classNode.fields.findAll {FieldNode fieldNode -> fieldNode.getAnnotations(COUCH_ID) || fieldNode.getAnnotations(PERSISTENCE_ID) }
        PropertyNode identity = getProperty(classNode, IDENTITY)

        if (nodes) {
            // look to see if the identity field was injected and isn't one of our annotated field(s)
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
                    identity.field.type = STRING_TYPE
                }
            } else {

                if (log.isDebugEnabled()) {
                    log.debug("Adding property [" + IDENTITY + "] to class [" + classNode.getName() + "]")
                }
                identity = classNode.addProperty(IDENTITY, Modifier.PUBLIC, STRING_TYPE, null, null, null)
            }

            nodes = [identity.field]
        }

        // set the jcouchdb JSONProperty annotation
        nodes.each {FieldNode field ->
            setJsonPropertyAnnotation(field, ["value": new ConstantExpression("_id"), "ignoreIfNull": ConstantExpression.TRUE])
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
            // look to see if the version field was injected and isn't one of our annotated field(s)
            if (version && version.field.lineNumber < 0 && !nodes.findAll {FieldNode fieldNode -> fieldNode.name == version.name}) {

                // remove the old version
                removeProperty(classNode, version.name)
            }

        } else {

            // if we don't have an annotated version then look for a plain version field
            if (version) {
                if (version.type.typeClass != String.class && version.field.lineNumber < 0) {
                    if (log.isDebugEnabled()) {
                        log.debug("Changing the type of property [" + VERSION + "] of class [" + classNode.getName() + "] to String.")
                    }
                    version.field.type = STRING_TYPE
                }
            } else {

                if (log.isDebugEnabled()) {
                    log.debug("Adding property [" + VERSION + "] to class [" + classNode.getName() + "]")
                }
                version = classNode.addProperty(VERSION, Modifier.PUBLIC, STRING_TYPE, null, null, null)
            }

            nodes = [version.field]
        }

        // set the jcouchdb JSONProperty annotation
        nodes.each {FieldNode field ->
            setJsonPropertyAnnotation(field, ["value": new ConstantExpression("_rev"), "ignoreIfNull": ConstantExpression.TRUE])
        }
    }

    private void injectAttachmentsProperty(ClassNode classNode) {
        Collection<FieldNode> nodes = classNode.fields.findAll {FieldNode fieldNode -> fieldNode.getAnnotations(COUCH_ATTACHMENTS) }

        if (!nodes) {

            // if we don't have an annotated id then look for an attachments field
            PropertyNode attachments = getProperty(classNode, "attachments")
            if (attachments == null) {

                // don't inject the attachments property if it doesn't exist ???
                return
            }

            nodes = [attachments.field]
        }

        // set the JSONProperty and JSONTypeHint annotations
        nodes.each {FieldNode field ->
            setJsonPropertyAnnotation(field, ["value": new ConstantExpression("_attachments"), "ignoreIfNull": ConstantExpression.TRUE])
            setJsonTypeHintAnnotation(field, ["value": new ClassExpression(new ClassNode(Attachment.class))])
        }
    }

    private void injectJSONPropertyAnnotations(ClassNode classNode) {

        // build the list of transients from the static transients statement (if any)
        List transients = []
        PropertyNode property = getProperty(classNode, "transients")
        if (property) {
            if (property.field.initialValueExpression instanceof ListExpression) {
                property.field.initialValueExpression.expressions.each {Expression e ->
                    if (e instanceof ConstantExpression) {
                        transients << e.value
                    }
                }
            }
        }

        List<AnnotationNode> annotations

        // if we have a property that has a getter but no setter, then add the "readOnly" JSONProperty annotation
        for (MethodNode method: classNode.getMethods()) {
            String getterName = method.name
            if (!method.isPrivate() && (getterName.startsWith("get") || (getterName.startsWith("is") && method.getReturnType() == BOOLEAN_TYPE))) {
                String fieldName = getterName.substring(getterName.startsWith("get") ? 3 : 2)

                // check @Transient
                if (method.getAnnotations(PERSISTENCE_TRANSIENT).size()) {
                    setJsonPropertyAnnotation(method, ["ignore": ConstantExpression.TRUE])
                }

                // now check for a setter and set readOnly if there isn't one
                String setterName = "set" + fieldName
                if (!classNode.getSetterMethod(setterName)) {
                    setJsonPropertyAnnotation(method, ["readOnly": ConstantExpression.TRUE])
                }
            }
        }

        // find any JSONProperty field annotations, and inject them into the getter/setter
        // we have to do this because svenson expects the annotations to be on these methods
        // instead of the field
        for (FieldNode field: classNode.getFields()) {

            // if this is a transient field, then tell JSON to ignore it
            if (field.getAnnotations(PERSISTENCE_TRANSIENT) || transients.contains(field.name)) {
                setJsonPropertyAnnotation(field, ["ignore": ConstantExpression.TRUE])
            }

            annotations = field.getAnnotations(JSON_PROPERTY)
            annotations.addAll(field.getAnnotations(JSON_TYPE_HINT))
            annotations.addAll(field.getAnnotations(JSON_CONVERTER))

            if (annotations.size()) {

                // add all of the JSON_PROPERTY annotations to the getter (create it if necessary)
                String getterName = ((field.getType() == BOOLEAN_TYPE) ? "is" : "get") + Verifier.capitalize(field.name)
                MethodNode getter = classNode.getGetterMethod(getterName)
                if (getter == null) {
                    getter = new MethodNode(getterName,
                            Modifier.PUBLIC,
                            field.getType(),
                            Parameter.EMPTY_ARRAY,
                            null,
                            new ReturnStatement(
                                    new FieldExpression(field)
                            ))

                    classNode.addMethod(getter)
                }
                if (getter != null) {
                    getter.addAnnotations(annotations)
                }

                // same for the setter
                String setterName = "set" + Verifier.capitalize(field.name)
                MethodNode setter = classNode.getSetterMethod(setterName)
                if (setter == null) {
                    setter = new MethodNode(setterName,
                            Modifier.PUBLIC,
                            ClassHelper.VOID_TYPE,
                            new Parameter(field.getType(), "value") as Parameter[],
                            null,
                            new ExpressionStatement(
                                    new BinaryExpression(
                                            new FieldExpression(field),
                                            Token.newSymbol(Types.EQUAL, -1, -1),
                                            new VariableExpression("value"))))

                    classNode.addMethod(setter)
                }
                if (setter != null) {
                    setter.addAnnotations(annotations)
                }

                // remove the JSON annotations on the field
                for (Iterator it = field.annotations.iterator(); it.hasNext();) {
                    AnnotationNode node = (AnnotationNode) it.next()
                    if (JSON_PROPERTY.equals(node.getClassNode()) ||
                            JSON_TYPE_HINT.equals(node.getClassNode()) ||
                            JSON_CONVERTER.equals(node.getClassNode())) {

                        it.remove()
                    }
                }
            }
        }
    }

    private void setJsonPropertyAnnotation(AnnotatedNode owner, Map members) {
        List<AnnotationNode> annotations = owner.getAnnotations(JSON_PROPERTY)

        if (!annotations.size()) {
            AnnotationNode annotation = new AnnotationNode(JSON_PROPERTY)
            annotation.runtimeRetention = true
            annotation.allowedTargets = AnnotationNode.METHOD_TARGET
            owner.addAnnotation(annotation)

            annotations = owner.getAnnotations(JSON_PROPERTY)
        }

        members.each {String key, Expression value ->
            annotations[0].setMember(key, value)
        }
    }

    private void setJsonTypeHintAnnotation(AnnotatedNode owner, Map members) {
        List<AnnotationNode> annotations = owner.getAnnotations(JSON_TYPE_HINT)

        if (!annotations.size()) {
            AnnotationNode annotation = new AnnotationNode(JSON_TYPE_HINT)
            annotation.runtimeRetention = true
            annotation.allowedTargets = AnnotationNode.METHOD_TARGET
            owner.addAnnotation(annotation)

            annotations = owner.getAnnotations(JSON_TYPE_HINT)
        }

        members.each {String key, Expression value ->
            annotations[0].setMember(key, value)
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
