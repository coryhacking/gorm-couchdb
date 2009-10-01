package com.clearboxmedia.couchdb

import net.sf.json.JsonConfig
import net.sf.json.processors.JsonValueProcessor
import org.apache.commons.lang.time.DateFormatUtils

class CouchJsonDateValueProcessor implements JsonValueProcessor {

    private String datePattern

    def CouchJsonDateValueProcessor() {
        datePattern = "yyyy/MM/dd HH:mm:ss Z"
    }

    def CouchJsonDateValueProcessor(datePattern) {
        this.datePattern = datePattern;
    }

    public String getDatePattern() {
        return datePattern;
    }

    Object processArrayValue(Object bean, JsonConfig jsonConfig) {
        return null
    }

    Object processObjectValue(String key, Object bean, JsonConfig jsonConfig) {
        String value = null

        if (bean instanceof java.sql.Date) {
            bean = new Date(((java.sql.Date) bean).getTime());
        }

        if (bean instanceof Date) {
            value = DateFormatUtils.formatUTC((Date) bean, datePattern)
        }

        return value
    }
}
