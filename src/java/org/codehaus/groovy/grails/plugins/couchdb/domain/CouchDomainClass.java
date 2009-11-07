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
package org.codehaus.groovy.grails.plugins.couchdb.domain;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.groovy.grails.commons.AbstractGrailsClass;
import org.codehaus.groovy.grails.commons.GrailsDomainClass;
import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty;
import org.codehaus.groovy.grails.commons.GrailsDomainConfigurationUtil;
import org.codehaus.groovy.grails.exceptions.GrailsDomainException;
import org.codehaus.groovy.grails.validation.ConstrainedProperty;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.Validator;

import grails.plugins.couchdb.CouchAttachments;
import grails.plugins.couchdb.CouchEntity;
import grails.plugins.couchdb.CouchId;
import grails.plugins.couchdb.CouchVersion;
import grails.util.GrailsNameUtils;

import javax.persistence.Id;
import javax.persistence.Transient;
import javax.persistence.Version;
import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.annotation.IncompleteAnnotationException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Warner Onstine, Cory Hacking
 */
public class CouchDomainClass extends AbstractGrailsClass implements GrailsDomainClass {

    private static final Log log = LogFactory.getLog(CouchDomainClass.class);

    private Map<String, GrailsDomainClassProperty> propertyMap = new HashMap<String, GrailsDomainClassProperty>();
    private GrailsDomainClassProperty[] propertiesArray;

    private Map<String, GrailsDomainClassProperty> persistentProperties = new HashMap<String, GrailsDomainClassProperty>();
    private GrailsDomainClassProperty[] persistentPropertyArray;

    private CouchDomainClassProperty identifier;
    private CouchDomainClassProperty version;
    private CouchDomainClassProperty attachments;
    private String type;
    private String typeFieldName;
    private String designName;

    private Map constraints = new HashMap();
    private Validator validator;

    public CouchDomainClass(Class clazz) {
        super(clazz, "");

        CouchEntity entityAnnotation = (CouchEntity) clazz.getAnnotation(CouchEntity.class);
        if (entityAnnotation == null) {
            throw new GrailsDomainException("Class [" + clazz.getName() + "] is not annotated with grails.plugins.couchdb.CouchEntity!");
        }

        // try to read the "type" annotation property
        try {
            type = entityAnnotation.type();

            // if the type annotation is empty, then disable type handling
            if ("".equals(type)) {
                type = null;
            }
        } catch (IncompleteAnnotationException ex) {

            // if the type wasn't set, then use the domain class name
            type = clazz.getSimpleName().toLowerCase();
        }

        // try to read the "type" annotation property
        try {
            typeFieldName = entityAnnotation.typeFieldName();
            if ("".equals(typeFieldName)) {
                typeFieldName = "type";
            } else if ("class".equals(typeFieldName) || "metaClass".equals(typeFieldName)) {
                throw new GrailsDomainException("The CouchEntity annotation parameter [typeFieldName] on Class [" + clazz.getName() + "] cannot be set to 'class' or 'metaClass'.");
            }
        } catch (IncompleteAnnotationException ex) {
            typeFieldName = "type";
        }

        // we always want a default design name even if the type is disabled
        designName = (type != null) ? type : clazz.getSimpleName().toLowerCase();

        PropertyDescriptor[] descriptors = BeanUtils.getPropertyDescriptors(clazz);
        evaluateClassProperties(descriptors);

        // process the constraints
        try {
            this.constraints = GrailsDomainConfigurationUtil.evaluateConstraints(getReference().getWrappedInstance(), this.persistentPropertyArray);
        } catch (IntrospectionException e) {
            log.error("Error reading class [" + getClazz() + "] constraints: " + e.getMessage(), e);
        }

    }

    private void evaluateClassProperties(PropertyDescriptor[] descriptors) {

        for (PropertyDescriptor descriptor : descriptors) {
            if (GrailsDomainConfigurationUtil.isNotConfigurational(descriptor)) {
                final CouchDomainClassProperty property = new CouchDomainClassProperty(this, descriptor);

                if (property.isAnnotatedWith(CouchId.class) || property.isAnnotatedWith(Id.class)) {
                    this.identifier = property;
                } else if (property.isAnnotatedWith(CouchVersion.class) || property.isAnnotatedWith(Version.class)) {
                    this.version = property;
                } else if (property.isAnnotatedWith(CouchAttachments.class)) {
                    this.attachments = property;
                } else {
                    propertyMap.put(descriptor.getName(), property);
                    if (property.isPersistent()) {
                        persistentProperties.put(descriptor.getName(), property);
                    }
                }
            }
        }

        // if we don't have an annotated identifier, version, or attachments then try to find fields
        //  with the simple names and use them...
        if (this.identifier == null) {
            if (propertyMap.containsKey(GrailsDomainClassProperty.IDENTITY)) {
                this.identifier = (CouchDomainClassProperty) propertyMap.get(GrailsDomainClassProperty.IDENTITY);
                propertyMap.remove(GrailsDomainClassProperty.IDENTITY);
                persistentProperties.remove(GrailsDomainClassProperty.IDENTITY);
            } else {
                throw new GrailsDomainException("Identity property not found, but required in domain class [" + getFullName() + "]");
            }
        }

        if (this.identifier.type != String.class) {
            throw new GrailsDomainException("Identity property in domain class [" + getFullName() + "] must be a String.");
        }

        if (this.version == null) {
            if (propertyMap.containsKey(GrailsDomainClassProperty.VERSION)) {
                this.version = (CouchDomainClassProperty) propertyMap.get(GrailsDomainClassProperty.VERSION);
                propertyMap.remove(GrailsDomainClassProperty.VERSION);
                persistentProperties.remove(GrailsDomainClassProperty.VERSION);
            } else {
                throw new GrailsDomainException("Version property not found, but required in domain class [" + getFullName() + "]");
            }
        }

        if (this.version.type != String.class) {
            throw new GrailsDomainException("Version property in domain class [" + getFullName() + "] must be a String.");
        }

        if (this.attachments == null) {
            if (propertyMap.containsKey("attachments")) {
                this.attachments = (CouchDomainClassProperty) propertyMap.get("attachments");

                propertyMap.remove("attachments");
                persistentProperties.remove("attachments");
            }
        }

        this.identifier.setIdentity(true);

        // convert to arrays for optimization
        propertiesArray = propertyMap.values().toArray(new GrailsDomainClassProperty[propertyMap.size()]);
        persistentPropertyArray = persistentProperties.values().toArray(new GrailsDomainClassProperty[persistentProperties.size()]);

    }

    public boolean isOwningClass(Class domainClass) {
        return false;
    }

    public GrailsDomainClassProperty[] getProperties() {
        return propertiesArray;
    }

    public GrailsDomainClassProperty[] getPersistantProperties() {
        return getPersistentProperties();
    }

    public GrailsDomainClassProperty[] getPersistentProperties() {
        return persistentPropertyArray;
    }

    public GrailsDomainClassProperty getIdentifier() {
        return this.identifier;
    }

    public GrailsDomainClassProperty getVersion() {
        return this.version;
    }

    public GrailsDomainClassProperty getAttachments() {
        return this.attachments;
    }

    public String getType() {
        return this.type;
    }

    public String getTypeFieldName() {
        return this.typeFieldName;
    }

    public String getDesignName() {
        return designName;
    }

    public Map getAssociationMap() {
        return Collections.EMPTY_MAP;
    }

    public GrailsDomainClassProperty getPropertyByName(String name) {
        return propertyMap.get(name);
    }

    public String getFieldName(String propertyName) {
        GrailsDomainClassProperty prop = getPropertyByName(propertyName);
        return prop != null ? prop.getFieldName() : null;
    }

    public boolean isOneToMany(String propertyName) {
        GrailsDomainClassProperty prop = getPropertyByName(propertyName);
        return prop != null && prop.isOneToMany();
    }

    public boolean isManyToOne(String propertyName) {
        GrailsDomainClassProperty prop = getPropertyByName(propertyName);
        return prop != null && prop.isManyToOne();
    }

    public boolean isBidirectional(String propertyName) {
        return false;
    }

    public Class getRelatedClassType(String propertyName) {
        GrailsDomainClassProperty prop = getPropertyByName(propertyName);
        return prop != null ? prop.getType() : null;
    }

    public Map getConstrainedProperties() {
        return Collections.unmodifiableMap(this.constraints);
    }

    public Validator getValidator() {
        return this.validator;
    }

    public void setValidator(Validator validator) {
        this.validator = validator;
    }

    public String getMappingStrategy() {
        return "CouchDB";
    }

    public boolean isRoot() {
        return true;
    }

    public Set<GrailsDomainClass> getSubClasses() {
        return Collections.emptySet();
    }

    public void refreshConstraints() {
        try {
            this.constraints = GrailsDomainConfigurationUtil.evaluateConstraints(getReference().getWrappedInstance(), this.persistentPropertyArray);

            // Embedded components have their own ComponentDomainClass
            // instance which won't be refreshed by the application.
            // So, we have to do it here.
            for (GrailsDomainClassProperty property : this.persistentPropertyArray) {
                if (property.isEmbedded()) {
                    property.getComponent().refreshConstraints();
                }
            }
        } catch (IntrospectionException e) {
            log.error("Error reading class [" + getClazz() + "] constraints: " + e.getMessage(), e);
        }
    }

    public boolean hasSubClasses() {
        return false;
    }

    public Map getMappedBy() {
        return Collections.EMPTY_MAP;
    }

    public boolean hasPersistentProperty(String propertyName) {
        GrailsDomainClassProperty prop = getPropertyByName(propertyName);
        return prop != null && prop.isPersistent();
    }

    public void setMappingStrategy(String strategy) {
        // do nothing
    }

    private class CouchDomainClassProperty implements GrailsDomainClassProperty {

        private Class ownerClass;
        private PropertyDescriptor descriptor;
        private Field field;
        private String name;
        private Class type;
        private GrailsDomainClass domainClass;
        private Method getter;
        private boolean persistent = true;
        private boolean identity = false;

        public CouchDomainClassProperty(GrailsDomainClass domain, PropertyDescriptor descriptor) {
            this.ownerClass = domain.getClazz();
            this.domainClass = domain;
            this.descriptor = descriptor;
            this.name = descriptor.getName();
            this.type = descriptor.getPropertyType();
            this.getter = descriptor.getReadMethod();

            try {
                this.field = domain.getClazz().getDeclaredField(descriptor.getName());
            } catch (NoSuchFieldException e) {
                // ignore
            }

            this.persistent = checkPersistence(descriptor);

            checkIfTransient();
        }

        private boolean checkPersistence(PropertyDescriptor descriptor) {
            if (descriptor.getName().equals("class") || descriptor.getName().equals("metaClass")) {
                return false;
            }
            return true;
        }

        // Checks whether this property is transient... copied from DefaultGrailsDomainClassProperty.
        private void checkIfTransient() {
            if (isAnnotatedWith(Transient.class)) {
                this.persistent = false;

            } else {
                List transientProps = getTransients(domainClass);
                if (transientProps != null) {
                    for (Iterator i = transientProps.iterator(); i.hasNext();) {

                        // make sure its a string otherwise ignore. Note: Again maybe a warning?
                        Object currentObj = i.next();
                        if (currentObj instanceof String) {
                            String propertyName = (String) currentObj;

                            // if the property name is on the not persistant list
                            // then set persistant to false
                            if (propertyName.equals(this.name)) {
                                this.persistent = false;
                                break;
                            }
                        }
                    }
                }
            }
        }

        // Retrieves the transient properties... copied from DefaultGrailsDomainClassProperty.
        private List getTransients(GrailsDomainClass domainClass) {
            List transientProps;
            transientProps = (List) domainClass.getPropertyValue(TRANSIENT, List.class);

            // Undocumented feature alert! Steve insisted on this :-)
            List evanescent = (List) domainClass.getPropertyValue(EVANESCENT, List.class);
            if (evanescent != null) {
                if (transientProps == null) {
                    transientProps = new ArrayList();
                }

                transientProps.addAll(evanescent);
            }
            return transientProps;
        }

        public int getFetchMode() {
            return FETCH_EAGER;
        }

        public String getName() {
            return this.name;
        }

        public Class getType() {
            return this.type;
        }

        public Class getReferencedPropertyType() {
            if (Collection.class.isAssignableFrom(getType())) {
                final Type genericType = field.getGenericType();
                if (genericType instanceof ParameterizedType) {
                    final Type[] arguments = ((ParameterizedType) genericType).getActualTypeArguments();
                    if (arguments.length > 0) {
                        return (Class) arguments[0];
                    }
                }
            }
            return getType();
        }

        public GrailsDomainClassProperty getOtherSide() {
            return null;
        }

        public String getTypePropertyName() {
            return GrailsNameUtils.getPropertyName(getType());
        }

        public GrailsDomainClass getDomainClass() {
            return domainClass;
        }

        public boolean isPersistent() {

            return persistent;

        }

        public boolean isOptional() {
            ConstrainedProperty constrainedProperty = (ConstrainedProperty) domainClass.getConstrainedProperties().get(name);
            return (constrainedProperty != null) && constrainedProperty.isNullable();
        }

        public boolean isIdentity() {
            return identity;
        }

        public void setIdentity(boolean identity) {
            this.identity = identity;
        }

        public boolean isOneToMany() {
            return false;
        }

        public boolean isManyToOne() {
            return false;
        }

        public boolean isManyToMany() {
            return false;
        }

        public boolean isBidirectional() {
            return false;
        }

        public String getFieldName() {
            return getName().toUpperCase();
        }

        public boolean isOneToOne() {
            return false;
        }

        public GrailsDomainClass getReferencedDomainClass() {
            return null;
        }

        public boolean isAssociation() {
            return false;
        }

        public boolean isEnum() {
            return false;
        }

        public String getNaturalName() {
            return GrailsNameUtils.getNaturalName(getShortName());
        }

        public void setReferencedDomainClass(GrailsDomainClass referencedGrailsDomainClass) {

        }

        public void setOtherSide(GrailsDomainClassProperty referencedProperty) {

        }

        public boolean isInherited() {
            return false;
        }

        public boolean isOwningSide() {
            return false;
        }

        public boolean isCircular() {
            return getType().equals(ownerClass);
        }

        public String getReferencedPropertyName() {
            return null;
        }

        public boolean isEmbedded() {
            return false;
        }

        public GrailsDomainClass getComponent() {
            return null;
        }

        public void setOwningSide(boolean b) {

        }

        public boolean isBasicCollectionType() {
            return false;
        }

        public boolean isAnnotatedWith(Class annotation) {
            return (field != null && field.getAnnotation(annotation) != null) || (getter != null && getter.getAnnotation(annotation) != null);
        }

        // grails 1.2 GrailsDomainClass
        public boolean isHasOne() {
            return false;
        }

        public String toString() {
            String assType = null;
            if (isManyToMany()) {
                assType = "many-to-many";
            } else if (isOneToMany()) {
                assType = "one-to-many";
            } else if (isOneToOne()) {
                assType = "one-to-one";
            } else if (isManyToOne()) {
                assType = "many-to-one";
            } else if (isEmbedded()) {
                assType = "embedded";
            }
            return new ToStringBuilder(this).append("name", this.name).append("type", this.type).append("persistent", isPersistent()).append("optional", isOptional()).append("association", isAssociation()).append("bidirectional", isBidirectional()).append("association-type", assType).toString();
        }
    }
}
