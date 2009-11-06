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
package grails.plugins.couchdb.json;

import org.apache.commons.lang.time.DateFormatUtils;

import net.sf.ezmorph.MorphUtils;
import net.sf.ezmorph.MorpherRegistry;
import net.sf.ezmorph.object.DateMorpher;

import java.util.Date;

/**
 * @author Cory Hacking
 */
public class JsonConverterUtils {

    public static final String DATE_PATTERN = "yyyy/MM/dd HH:mm:ss Z";

    private static final MorpherRegistry morpher = new MorpherRegistry();
    private static final DateMorpher dm = new DateMorpher(new String[]{DATE_PATTERN, "yyyy/MM/dd HH:mm:ss.S Z", "yyyy/MM/dd HH:mm:ssZ", "yyyy/MM/dd HH:mm:ss.SZ", "yyyy/MM/dd HH:mm:ss.S z", "yyyy/MM/dd HH:mm:ss z", "EEE, dd MMM yyyy HH:mm:ssZ", "EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss z"}, true);

    static {
        MorphUtils.registerStandardMorphers(morpher);
        morpher.registerMorpher(dm);
    }

    public static Object fromJSON(Class target, Object value) {
        return morpher.morph(target, value);
    }

    public static Object toJSON(Object in) {
        Object value = in;

        if (value instanceof java.sql.Date || value instanceof java.sql.Timestamp) {
            value = new Date(((Date) value).getTime());
        }

        if (value instanceof Date) {
            value = DateFormatUtils.formatUTC((Date) value, DATE_PATTERN);
        }

        return value;
    }

    private JsonConverterUtils() {

    }
}
