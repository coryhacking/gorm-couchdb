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

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.Validator;
import grails.plugins.couchdb.CouchAttachments;
import grails.plugins.couchdb.CouchEntity;
import grails.plugins.couchdb.CouchId;
import grails.plugins.couchdb.CouchVersion;
import org.codehaus.groovy.grails.commons.AbstractGrailsClass;
import org.codehaus.groovy.grails.commons.ExternalGrailsDomainClass;
import org.codehaus.groovy.grails.commons.GrailsDomainClass;
import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty;
import org.codehaus.groovy.grails.commons.GrailsDomainConfigurationUtil;
import org.codehaus.groovy.grails.exceptions.GrailsDomainException;
import org.codehaus.groovy.grails.validation.GrailsDomainClassValidator;

import javax.persistence.Id;
import javax.persistence.Version;
import java.beans.PropertyDescriptor;
import java.lang.annotation.IncompleteAnnotationException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Warner Onstine, Cory Hacking
 */
public class CouchDomainClass extends AbstractGrailsClass implements ExternalGrailsDomainClass {

	private static final Log log = LogFactory.getLog(CouchDomainClass.class);

	private static final String COUCH_MAPPING_STRATEGY = "CouchDB";

	private Map<String, GrailsDomainClassProperty> propertyMap = new HashMap<String, GrailsDomainClassProperty>();
	private GrailsDomainClassProperty[] propertiesArray;

	private Map<String, GrailsDomainClassProperty> persistentProperties = new HashMap<String, GrailsDomainClassProperty>();
	private GrailsDomainClassProperty[] persistentPropertyArray;

	private CouchDomainClassProperty identifier;
	private CouchDomainClassProperty version;
	private CouchDomainClassProperty attachments;

	private String databaseId;
	private String designName;
	private String documentType;
	private String typeFieldName;

	private Map constraints = new HashMap();
	private Validator validator;

	private boolean shouldFailOnError;

	private Map<String, Object> defaultConstraints;

	private Set subClasses = new HashSet();

	private Map<String, GrailsDomainClass> subClassTypes = new HashMap<String, GrailsDomainClass>();

	public CouchDomainClass(Class<?> clazz, Map<String, Object> defaultConstraints, boolean shouldFailOnError) {
		super(clazz, "");

		CouchEntity entityAnnotation = (CouchEntity) clazz.getAnnotation(CouchEntity.class);
		if (entityAnnotation == null) {
			throw new GrailsDomainException("Class [" + clazz.getName() + "] is not annotated with grails.plugins.couchdb.CouchEntity!");
		}

		this.defaultConstraints = defaultConstraints;
		this.shouldFailOnError = shouldFailOnError;

		// try to read the "designName" annotation property
		try {
			designName = entityAnnotation.designName();
			if ("".equals(designName)) {
				designName = null;
			}
		} catch (IncompleteAnnotationException ex) {
			designName = null;
		}

		// if the design wasn't set, then try to use the "type" annotation property
		if (designName == null) {
			try {
				designName = entityAnnotation.type();
			} catch (IncompleteAnnotationException ex) {
				designName = null;
			}
		}

		// if designName is still empty, then use the class name
		if (designName == null || "".equals(designName)) {
			designName = clazz.getSimpleName().toLowerCase();
		}

		if ("class".equals(designName) || "metaClass".equals(designName)) {
			throw new GrailsDomainException("The CouchEntity annotation parameter [designName] on Class [" + clazz.getName() + "] cannot be set to 'class' or 'metaClass'.");
		}

		// try to read the "db" annotation property
		try {
			databaseId = entityAnnotation.db();

			// if the db annotation is empty, then just use the default
			if ("".equals(databaseId)) {
				databaseId = null;
			}
		} catch (IncompleteAnnotationException ex) {
			databaseId = null;
		}

		// set the full documentType
		try {
			typeFieldName = entityAnnotation.typeFieldName();
			if (typeFieldName != null && !"".equals(typeFieldName)) {
				Method getter = clazz.getDeclaredMethod("get" + StringUtils.capitalize(typeFieldName));
				documentType = (String) getter.invoke(clazz.newInstance());
			}
		} catch (Exception e) {
			if (e instanceof NoSuchMethodException && "".equals(entityAnnotation.type())) {
				log.info("Document type is disabled for Class [" + clazz.getName() + "].");
			} else {
				log.error("Couldn't get document type for Class [" + clazz.getName() + "].", e);
			}

			documentType = "";
		}

		PropertyDescriptor[] descriptors = BeanUtils.getPropertyDescriptors(clazz);
		evaluateClassProperties(descriptors);

		// process the constraints
		try {
			initializeConstraints();
		} catch (Exception e) {
			log.error("Error reading class [" + getClazz() + "] constraints: " + e.getMessage(), e);
		}
	}

	public CouchDomainClass(Class<?> clazz) {
		this(clazz, null, false);
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

		if (this.identifier.getType() != String.class) {
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

		if (this.version.getType() != String.class) {
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

	/**
	 * Returns all of the persistant properties of the domain class
	 *
	 * @return The domain class' persistant properties
	 *
	 * @deprecated Use #getPersistentProperties instead
	 */
	public GrailsDomainClassProperty[] getPersistantProperties() {
		return getPersistentProperties();
	}

	public GrailsDomainClassProperty[] getPersistentProperties() {
		return persistentPropertyArray;
	}

	public GrailsDomainClassProperty getPersistentProperty(String name) {
		return persistentProperties.get(name);
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

	public boolean getShouldFailOnError() {
		return shouldFailOnError;
	}

	public String getDesignName() {
		return designName;
	}

	public String getDatabaseId() {
		return databaseId;
	}

	public String getTypeFieldName() {
		return typeFieldName;
	}

	public String getDocumentType() {
		return documentType;
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
		if (constraints == null) {
			initializeConstraints();
		}
		return Collections.unmodifiableMap(constraints);
	}

	private void initializeConstraints() {
		// process the constraints
		if (defaultConstraints != null) {
			constraints = GrailsDomainConfigurationUtil.evaluateConstraints(getClazz(), persistentPropertyArray, defaultConstraints);
		} else {
			constraints = GrailsDomainConfigurationUtil.evaluateConstraints(getClazz(), persistentPropertyArray);
		}
	}

	public Validator getValidator() {
		if (validator == null) {
			GrailsDomainClassValidator gdcv = new GrailsDomainClassValidator();
			gdcv.setDomainClass(this);
			validator = gdcv;
		}
		return validator;
	}

	public void setValidator(Validator validator) {
		this.validator = validator;
	}

	public String getMappingStrategy() {
		return COUCH_MAPPING_STRATEGY;
	}

	public boolean isRoot() {
		return getClazz().getSuperclass().equals(Object.class);
	}

	public boolean hasSubClasses() {
		return subClasses.size() > 0;
	}

	@SuppressWarnings ({"unchecked"})
	public Set<GrailsDomainClass> getSubClasses() {
		return subClasses;
	}

	public Map<String, GrailsDomainClass> getSubClassTypes() {
		return subClassTypes;
	}

	public void refreshConstraints() {
		if (defaultConstraints != null) {
			constraints = GrailsDomainConfigurationUtil.evaluateConstraints(getClazz(), persistentPropertyArray, defaultConstraints);
		} else {
			constraints = GrailsDomainConfigurationUtil.evaluateConstraints(getClazz(), persistentPropertyArray);
		}

		// Embedded components have their own ComponentDomainClass instance which
		// won't be refreshed by the application. So, we have to do it here.
		for (GrailsDomainClassProperty property : persistentPropertyArray) {
			if (property.isEmbedded()) {
				property.getComponent().refreshConstraints();
			}
		}
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
}
