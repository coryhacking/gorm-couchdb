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

class CouchDocument implements Document, JSONable {

    private static JsonConfig jsonConfig = new CouchJsonConfig()

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
        json.accumulateAll attrs, jsonConfig

        // return the json string
        return JSONSerializer.toJSON(json, jsonConfig).toString()
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