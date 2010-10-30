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
import groovy.util.ConfigObject;
import groovy.util.Eval;

import grails.plugins.couchdb.CouchEntity;
import grails.util.ClosureToMapPopulator;
import org.codehaus.groovy.grails.commons.ArtefactHandlerAdapter;
import org.codehaus.groovy.grails.commons.GrailsClass;
import org.codehaus.groovy.grails.commons.GrailsClassUtils;
import org.codehaus.groovy.grails.plugins.support.aware.GrailsConfigurationAware;
import java.util.Map;

/**
 * @author Warner Onstine, Cory Hacking
 */
public class CouchDomainClassArtefactHandler extends ArtefactHandlerAdapter implements GrailsConfigurationAware {

	private static final String COUCH_MAPPING_STRATEGY = "CouchDB";
	public static final String TYPE = "CouchDomain";

	private Map defaultConstraints;

	public CouchDomainClassArtefactHandler() {
		super(TYPE, CouchDomainClass.class, CouchDomainClass.class, null);
	}

	@SuppressWarnings ({"unchecked"})
	public GrailsClass newArtefactClass(Class artefactClass) {
		if (defaultConstraints != null) {
			return new CouchDomainClass(artefactClass, defaultConstraints);
		}
		return new CouchDomainClass(artefactClass);
	}

	public Map getDefaultConstraints() {
		return defaultConstraints;
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
	}

}
