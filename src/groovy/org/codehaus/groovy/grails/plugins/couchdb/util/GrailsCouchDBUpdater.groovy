/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package org.codehaus.groovy.grails.plugins.couchdb.util

import org.apache.commons.io.FileUtils
import org.jcouchdb.document.DesignDocument
import org.jcouchdb.util.AbstractCouchDBUpdater
import org.jcouchdb.util.Assert

/**
 * @author Cory Hacking
 */
public class GrailsCouchDBUpdater extends AbstractCouchDBUpdater {

	private File designDocumentDir

	public void setDesignDocumentDir(File designDocumentDir) {
		Assert.isTrue(designDocumentDir.exists(), "designDocumentDir must exist")
		Assert.isTrue(designDocumentDir.isDirectory(), "designDocumentDir must actually be a directory")
		this.designDocumentDir = designDocumentDir
	}

	private String designName

	public void  setDesignName(String designName) {
		this.designName = designName
	}

	@Override
	protected List<DesignDocument> readDesignDocuments() throws IOException {

		Assert.notNull(designDocumentDir, "designDocumentDir can't be null")

		Map<String, DesignDocument> designDocuments = new HashMap<String, DesignDocument>()
		String designDocumentDirPath = designDocumentDir.getPath()

		def searchDir = (designName) ? new File(designDocumentDir, designName) : designDocumentDir
		Assert.isTrue(searchDir.exists(), "searchDir must exist")
		Assert.isTrue(searchDir.isDirectory(), "searchDir must actually be a directory")

		def files = FileUtils.listFiles(searchDir, ["js"] as String[], true)

		files.each {File file ->
			String path = file.getPath()
			Assert.isTrue(path.startsWith(designDocumentDirPath), "not in dir")

			path = path.substring(designDocumentDirPath.length())

			boolean isMapFunction = path.endsWith(MAP_SUFFIX)
			boolean isReduceFunction = path.endsWith(REDUCE_SUFFIX)
			if (isMapFunction || isReduceFunction) {
				String content = FileUtils.readFileToString(file)

				if (content != null && content.trim().length() > 0) {
					createViewFor(path, content, designDocuments, File.separator)
				}
			}
		}

		return new ArrayList<DesignDocument>(designDocuments.values())
	}
}