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
package com.clearboxmedia.couchdb.domain;

import com.clearboxmedia.couchdb.CouchJsonConfig
import net.sf.json.JSON
import net.sf.json.JSONObject
import net.sf.json.JSONSerializer
import net.sf.json.JsonConfig
import org.jcouchdb.document.Attachment
import org.jcouchdb.document.Document
import org.svenson.JSONable
import org.svenson.util.Util

/**
 *
 * @author Warner Onstine, Cory Hacking
 */
class CouchDocument implements Document, JSONable {

    private static final JsonConfig COUCH_JSON_CONFIG = new CouchJsonConfig()

    String id;
    String revision;
    Map<String, Attachment> attachments;

    // groovy dynamic properties
    private def attrs = [:]

    def propertyMissing(String name, value) {
        if (name.equals("_id")) {
            setId((String) value)

        } else if (name.equals("_rev")) {
            setRevision((String) value)

        } else if (name.equals("_attachments")) {
            setAttachments((Map) value)

        }
        attrs[name] = value
    }

    def propertyMissing(String name) {
        if (name.equals("_id")) {
            return getId()

        } else if (name.equals("_rev")) {
            return getRevision()

        } else if (name.equals("_attachments")) {
            return getAttachments()

        }
        attrs[name]
    }

    public void addAttachment(String name, Attachment attachment) {
        if (attachments == null) {
            attachments = new HashMap<String, Attachment>();
        }
        attachments.put(name, attachment);
    }

    String toJSON() {
        JSON json = new JSONObject()

        // set couchdb _id, _revision, and _attachments
        if (id) {
            json.accumulate '_id', id
        }

        if (revision) {
            json.accumulate '_rev', revision
        }

        if (attachments) {
            json.accumulate '_attachments', attachments
        }

        // set our remaining attributes
        json.accumulateAll attrs, COUCH_JSON_CONFIG

        // return the json string
        return JSONSerializer.toJSON(json, COUCH_JSON_CONFIG).toString()
    }

    @Override
    public String toString() {
        return super.toString() + ": _id = " + id + ", _rev = " + revision;
    }

    /**
     * Two documents are equal if they have the same id and the same revision.
     *
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Document) {
            Document that = (Document) obj;
            return Util.equals(this.getId(), that.getId()) && Util.equals(this.getRevision(), that.getRevision());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 17 + Util.safeHashcode(getId()) * 37 + Util.safeHashcode(getRevision()) * 37;
    }
}