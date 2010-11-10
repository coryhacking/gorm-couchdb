/*
 * Copyright $today.year the original author or authors.
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

import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import groovy.util.ConfigObject;
import groovy.util.Eval;

import grails.plugins.couchdb.CouchEntity;
import grails.util.ClosureToMapPopulator;
import org.codehaus.groovy.grails.commons.ArtefactHandlerAdapter;
import org.codehaus.groovy.grails.commons.ArtefactInfo;
import org.codehaus.groovy.grails.commons.GrailsClass;
import org.codehaus.groovy.grails.commons.GrailsClassUtils;
import org.codehaus.groovy.grails.commons.GrailsDomainClass;
import org.codehaus.groovy.grails.plugins.support.aware.GrailsConfigurationAware;
import java.util.List;
import java.util.Map;

/**
 * @author Warner Onstine, Cory Hacking
 */
public class CouchDomainClassArtefactHandler extends ArtefactHandlerAdapter implements GrailsConfigurationAware {

	public static final String TYPE = "CouchDomain";

	private Map defaultConstraints;
	private Object shouldFailConfigProperty;

	public CouchDomainClassArtefactHandler() {
		super(TYPE, CouchDomainClass.class, CouchDomainClass.class, null);
	}

	@SuppressWarnings ({"unchecked"})
	public GrailsClass newArtefactClass(Class artefactClass) {

		boolean shouldFailOnError = false;
		if (shouldFailConfigProperty instanceof Boolean) {
			shouldFailOnError = (Boolean.TRUE == shouldFailConfigProperty);
		} else if (shouldFailConfigProperty instanceof List) {
			if (artefactClass != null) {
				List packageList = (List) shouldFailConfigProperty;
				shouldFailOnError = GrailsClassUtils.isClassBelowPackage(artefactClass, packageList);
			}
		}

		return new CouchDomainClass(artefactClass, defaultConstraints, shouldFailOnError);
	}

	@Override
	public void initialize(ArtefactInfo artefacts) {
		log.debug("Configuring CouchDB domain class relationships...");

		Map domainMap = artefacts.getGrailsClassesByName();

		// configure sub class relationships
		for (GrailsClass grailsClass : artefacts.getGrailsClasses()) {
			CouchDomainClass domainClass = (CouchDomainClass) grailsClass;
			if (!domainClass.isRoot()) {
				Class<?> superClass = grailsClass.getClazz().getSuperclass();
				while (!superClass.equals(Object.class) && !superClass.equals(GroovyObject.class)) {
					GrailsDomainClass gdc = (GrailsDomainClass) domainMap.get(superClass.getName());
					if (gdc == null || gdc.getSubClasses() == null) {
						break;
					}

					gdc.getSubClasses().add((GrailsDomainClass) grailsClass);
					superClass = superClass.getSuperclass();
				}
			}
		}

		// configure the subClassTypes map
		for (GrailsClass grailsClass : artefacts.getGrailsClasses()) {
			CouchDomainClass domainClass = (CouchDomainClass) grailsClass;

			// initialize the subclasses
			if (domainClass.hasSubClasses()) {
				for (GrailsDomainClass subClass : domainClass.getSubClasses()) {

					CouchEntity annotation = (CouchEntity) subClass.getClazz().getAnnotation(CouchEntity.class);
					String typeFieldName = annotation.typeFieldName();

					String type = subClass.getPropertyValue(typeFieldName, String.class);
					if (type != null && !"".equals(type)) {
						domainClass.getSubClassTypes().put(type, subClass);
					}
				}
			}
		}
	}

	public boolean isArtefactClass(Class clazz) {
		return isCouchDomainClass(clazz);
	}

	@SuppressWarnings ({"unchecked"})
	public static boolean isCouchDomainClass(Class clazz) {

		// it's not a closure
		if (clazz == null) {
			return false;
		}

		if (Closure.class.isAssignableFrom(clazz)) {
			return false;
		}

		if (GrailsClassUtils.isJdk5Enum(clazz)) {
			return false;
		}

		return clazz.getAnnotation(CouchEntity.class) != null;
	}

	public void setConfiguration(ConfigObject co) {
		Object constraints = Eval.x(co, "x?.grails?.gorm?.default?.constraints");
		if (constraints instanceof Closure) {
			if (defaultConstraints != null) {
				// repopulate existing map
				defaultConstraints.clear();
				new ClosureToMapPopulator(defaultConstraints).populate((Closure) constraints);
			} else {
				ClosureToMapPopulator populator = new ClosureToMapPopulator();
				defaultConstraints = populator.populate((Closure) constraints);
			}
		}

		shouldFailConfigProperty = Eval.x(co, "x?.grails?.gorm?.failOnError");
	}
}
