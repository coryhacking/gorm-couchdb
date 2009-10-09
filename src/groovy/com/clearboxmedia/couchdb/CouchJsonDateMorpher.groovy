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
package com.clearboxmedia.couchdb

import net.sf.ezmorph.object.AbstractObjectMorpher
import org.apache.commons.lang.time.DateFormatUtils

/**
 *
 * @author Warner Onstine, Cory Hacking
 */
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
