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
package grails.plugins.couchdb

import java.sql.Timestamp
import net.sf.ezmorph.object.DateMorpher
import net.sf.json.JsonConfig
import net.sf.json.processors.JsonValueProcessor
import net.sf.json.util.JSONUtils

/**
 *
 * @author Warner Onstine, Cory Hacking
 */
public class CouchJsonConfig extends JsonConfig {

    private static JsonValueProcessor jvp = new CouchJsonDateValueProcessor()

    static {

        // register the date morpher with a few common date patterns
        DateMorpher dm = new DateMorpher(["yyyy/MM/dd HH:mm:ss Z", "yyyy/MM/dd HH:mm:ss.S Z", "yyyy/MM/dd HH:mm:ssZ", "yyyy/MM/dd HH:mm:ss.SZ", "yyyy/MM/dd HH:mm:ss.S z", "yyyy/MM/dd HH:mm:ss z", "EEE, dd MMM yyyy HH:mm:ssZ", "EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss z"] as String[], Locale.getDefault(), true)
        JSONUtils.morpherRegistry.registerMorpher dm, true

    }

    public static Object morph(Class target, Object value) {
        JSONUtils.getMorpherRegistry().morph(target, value)
    }

    public CouchJsonConfig() {

        // register our json date processors
        registerJsonValueProcessor Date.class, jvp
        registerJsonValueProcessor Timestamp.class, jvp
        registerJsonValueProcessor java.sql.Date.class, jvp

    }
}
