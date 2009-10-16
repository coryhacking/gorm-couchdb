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
package com.clearboxmedia.couchdb.cfg;

import com.clearboxmedia.couchdb.domain.CouchDomainClassArtefactHandler;
import org.codehaus.groovy.grails.commons.GrailsDomainClass;
import org.codehaus.groovy.grails.orm.hibernate.cfg.GrailsAnnotationConfiguration;
import org.codehaus.groovy.grails.orm.hibernate.cfg.GrailsDomainConfiguration;

/**
 * @author Cory Hacking
 */
public class CouchAnnotationConfiguration extends GrailsAnnotationConfiguration {

    private static final long serialVersionUID = 6586536745135709599L;

    @Override
    public GrailsDomainConfiguration addDomainClass(GrailsDomainClass domainClass) {
        if (!CouchDomainClassArtefactHandler.isCouchDomainClass(domainClass.getClazz())) {
            return super.addDomainClass(domainClass);
        }
        
        return this;
    }
}
