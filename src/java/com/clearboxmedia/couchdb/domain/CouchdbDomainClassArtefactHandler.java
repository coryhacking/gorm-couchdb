package com.clearboxmedia.couchdb.domain;

import com.clearboxmedia.couchdb.CouchDBEntity;
import org.codehaus.groovy.grails.commons.ArtefactHandlerAdapter;
import org.codehaus.groovy.grails.commons.GrailsDomainClass;

public class CouchdbDomainClassArtefactHandler extends ArtefactHandlerAdapter {

    public static final String TYPE = "Domain";

    public CouchdbDomainClassArtefactHandler() {
        super(TYPE, GrailsDomainClass.class, CouchdbGrailsDomainClass.class, null);
    }

    public boolean isArtefactClass(Class clazz) {
        return isCouchDBDomainClass(clazz);
    }

    public static boolean isCouchDBDomainClass(Class clazz){
        return clazz != null && clazz.getAnnotation(CouchDBEntity.class) != null;
    }
}
