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
package org.codehaus.groovy.grails.plugins.couchdb.json;

import org.svenson.AbstractPropertyValueBasedTypeMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Cory Hacking
 */
public class CouchDomainTypeMapper extends AbstractPropertyValueBasedTypeMapper {

	protected Map<String, Class> typeMap = new HashMap<String, Class>();

	/**
	 * Maps the given field value to the given type.
	 *
	 * @param value field value
	 * @param cls   type
	 */
	public void addFieldValueMapping(String value, Class cls) {
		typeMap.put(value, cls);
	}

	/**
	 * @return Class or <code>null</code>
	 */
	@Override
	protected Class getTypeHintFromTypeProperty(Object value) {
		return typeMap.get(value);
	}
}