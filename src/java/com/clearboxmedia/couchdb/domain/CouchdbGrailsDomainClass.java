package com.clearboxmedia.couchdb.domain;

import com.clearboxmedia.couchdb.CouchAttachments;
import com.clearboxmedia.couchdb.CouchEntity;
import com.clearboxmedia.couchdb.CouchId;
import com.clearboxmedia.couchdb.CouchRev;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.codehaus.groovy.grails.commons.AbstractGrailsClass;
import org.codehaus.groovy.grails.commons.GrailsDomainClass;
import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty;
import org.codehaus.groovy.grails.exceptions.GrailsDomainException;
import org.codehaus.groovy.grails.validation.ConstrainedProperty;
import org.codehaus.groovy.grails.validation.metaclass.ConstraintsEvaluatingDynamicProperty;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.Validator;

import grails.util.GrailsNameUtils;

import java.beans.PropertyDescriptor;
import java.lang.annotation.IncompleteAnnotationException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class CouchdbGrailsDomainClass extends AbstractGrailsClass implements GrailsDomainClass {

    private Map<String, GrailsDomainClassProperty> propertyMap = new HashMap<String, GrailsDomainClassProperty>();
    private Map<String, GrailsDomainClassProperty> persistentProperties = new HashMap<String, GrailsDomainClassProperty>();
    private GrailsDomainClassProperty[] propertiesArray;
    private CouchdbDomainClassProperty identifier;
    private CouchdbDomainClassProperty version;
    private CouchdbDomainClassProperty attachments;
    private String type;
    private Validator validator;
    private GrailsDomainClassProperty[] persistentPropertyArray;
    private Map constrainedProperties = Collections.EMPTY_MAP;

    public CouchdbGrailsDomainClass(Class clazz) {
        super(clazz, "");

        CouchEntity entityAnnotation = (CouchEntity) clazz.getAnnotation(CouchEntity.class);
        if (entityAnnotation == null) {
            throw new GrailsDomainException("Class [" + clazz.getName() + "] is not annotated with com.clearboxmedia.couchdb.CouchEntity!");
        }

        PropertyDescriptor[] descriptors = BeanUtils.getPropertyDescriptors(clazz);
        evaluateClassProperties(descriptors);
        evaluateConstraints();
        propertiesArray = propertyMap.values().toArray(new GrailsDomainClassProperty[propertyMap.size()]);
        persistentPropertyArray = persistentProperties.values().toArray(new GrailsDomainClassProperty[persistentProperties.size()]);

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
    }

    private void evaluateConstraints() {
        ConstraintsEvaluatingDynamicProperty constraintsEvaluator = new ConstraintsEvaluatingDynamicProperty(getProperties());
        this.constrainedProperties = (Map) constraintsEvaluator.get(getReference().getWrappedInstance());
    }

    private void evaluateClassProperties(PropertyDescriptor[] descriptors) {
        System.out.println("[evaluateClassProperties] called");
        for (PropertyDescriptor descriptor : descriptors) {

            final CouchdbDomainClassProperty property = new CouchdbDomainClassProperty(this, descriptor);

            if (property.isAnnotatedWith(CouchId.class)) {
                this.identifier = property;
            } else if (property.isAnnotatedWith(CouchRev.class)) {
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

        this.constrainedProperties = (Map) new ConstraintsEvaluatingDynamicProperty(getPersistentProperties()).get(getReference().getWrappedInstance());
    }

    public boolean isOwningClass(Class domainClass) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
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
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Class getRelatedClassType(String propertyName) {
        GrailsDomainClassProperty prop = getPropertyByName(propertyName);
        return prop != null ? prop.getType() : null;
    }

    public Map getConstrainedProperties() {
        return this.constrainedProperties;
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
        // NOOP
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

    private class CouchdbDomainClassProperty implements GrailsDomainClassProperty {

        private Class ownerClass;
        private PropertyDescriptor descriptor;
        private Field propertyField;
        private String name;
        private Class type;
        private GrailsDomainClass domainClass;
        private Method getter;
        private boolean persistent = true;
        private Field field;
        private boolean version;

        public CouchdbDomainClassProperty(GrailsDomainClass domain, PropertyDescriptor descriptor) {
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
        }

        private boolean checkPersistence(PropertyDescriptor descriptor) {
            if (descriptor.getName().equals("class") || descriptor.getName().equals("metaClass") || descriptor.getName().equals("version")) {
                return false;
            }
            return true;
        }

        public <T extends java.lang.annotation.Annotation> T getAnnotation(Class<T> annotation) {
            if (field == null) {
                return null;
            }
            return this.field.getAnnotation(annotation);
        }

        public int getFetchMode() {
            return FETCH_LAZY;
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
            return null;  //To change body of implemented methods use File | Settings | File Templates.
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
            return isAnnotatedWith(CouchId.class);
        }

        public boolean isOneToMany() {
            return false;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public boolean isManyToOne() {
            return false;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public boolean isManyToMany() {
            return false;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public boolean isBidirectional() {
            return false;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public String getFieldName() {
            return getName().toUpperCase();
        }

        public boolean isOneToOne() {
            return false;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public GrailsDomainClass getReferencedDomainClass() {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public boolean isAssociation() {
            return false;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public boolean isEnum() {
            return false;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public String getNaturalName() {
            return GrailsNameUtils.getNaturalName(getShortName());
        }

        public void setReferencedDomainClass(GrailsDomainClass referencedGrailsDomainClass) {
            //To change body of implemented methods use File | Settings | File Templates.
        }

        public void setOtherSide(GrailsDomainClassProperty referencedProperty) {
            //To change body of implemented methods use File | Settings | File Templates.
        }

        public boolean isInherited() {
            return false;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public boolean isOwningSide() {
            return false;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public boolean isCircular() {
            return getType().equals(ownerClass);
        }

        public String getReferencedPropertyName() {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public boolean isEmbedded() {
            return false;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public GrailsDomainClass getComponent() {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public void setOwningSide(boolean b) {
            //To change body of implemented methods use File | Settings | File Templates.
        }

        public boolean isBasicCollectionType() {
            return false;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public boolean isAnnotatedWith(Class annotation) {
            if (field == null) {
                return false;
            }
            return field.getAnnotation(annotation) != null;
        }

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
