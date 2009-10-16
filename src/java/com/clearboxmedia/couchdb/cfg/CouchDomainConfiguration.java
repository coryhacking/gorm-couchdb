/*
 * $Header: $
 * $Revision: $
 * $Date: $
 *
 * Copyright 2007 PrimeRevenue, Inc.
 */
package com.clearboxmedia.couchdb.cfg;

import com.clearboxmedia.couchdb.domain.CouchDomainClassArtefactHandler;
import org.codehaus.groovy.grails.commons.GrailsDomainClass;
import org.codehaus.groovy.grails.orm.hibernate.cfg.DefaultGrailsDomainConfiguration;
import org.codehaus.groovy.grails.orm.hibernate.cfg.GrailsDomainConfiguration;

/**
 * @author coryh
 * @version $Revision: $  $Date: $
 */
public class CouchDomainConfiguration extends DefaultGrailsDomainConfiguration {

    private static final long serialVersionUID = 6586536745135709599L;

    @Override
    public GrailsDomainConfiguration addDomainClass(GrailsDomainClass domainClass) {
        if (!CouchDomainClassArtefactHandler.isCouchDomainClass(domainClass.getClazz())) {
            return super.addDomainClass(domainClass);
        }

        return this;
    }
}
