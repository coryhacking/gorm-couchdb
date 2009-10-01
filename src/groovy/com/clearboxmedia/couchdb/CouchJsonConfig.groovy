package com.clearboxmedia.couchdb

import java.sql.Timestamp
import net.sf.ezmorph.object.DateMorpher
import net.sf.json.JsonConfig
import net.sf.json.processors.JsonValueProcessor
import net.sf.json.util.JSONUtils

public class CouchJsonConfig extends JsonConfig {

    private static JsonValueProcessor jvp = new CouchJsonDateValueProcessor()

    static {

        // register the date morpher with a few common date patterns
        DateMorpher dm = new DateMorpher(["yyyy/MM/dd HH:mm:ss Z", "yyyy/MM/dd HH:mm:ssZ", "yyyy/MM/dd HH:mm:ss z", "EEE, dd MMM yyyy HH:mm:ssZ", "EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss z"] as String[], Locale.getDefault(), true)
        JSONUtils.morpherRegistry.registerMorpher dm, true

    }

    public CouchJsonConfig() {

        // register our json date processors
        registerJsonValueProcessor Date.class, jvp
        registerJsonValueProcessor Timestamp.class, jvp
        registerJsonValueProcessor java.sql.Date.class, jvp

    }
}
