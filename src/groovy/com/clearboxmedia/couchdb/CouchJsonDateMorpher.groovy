package com.clearboxmedia.couchdb

import net.sf.ezmorph.object.AbstractObjectMorpher
import org.apache.commons.lang.time.DateFormatUtils

class CouchJsonDateMorpher extends AbstractObjectMorpher {

    private String datePattern

    def CouchJsonDateValueProcessor() {
        datePattern = "yyyy/MM/dd HH:mm:ss Z"
    }

    def CouchJsonDateMorpher(datePattern) {
        this.datePattern = datePattern;
    }

    public String getDatePattern() {
        return datePattern;
    }

    Object processObjectValue(String key, Object bean,  jsonConfig) {
        String value = null

        if (bean instanceof java.sql.Date) {
            bean = new Date(((java.sql.Date) bean).getTime());
        }

        if (bean instanceof Date) {
            value = DateFormatUtils.format((Date) bean, datePattern)
        }

        return value
    }

    Object morph(Object value) {
        return null;  
    }

    Class morphsTo() {
        return Date.class
    }
}
