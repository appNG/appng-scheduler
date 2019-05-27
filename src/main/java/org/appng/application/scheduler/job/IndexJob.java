/*
 * Copyright 2011-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.appng.application.scheduler.job;

import java.util.Map;

import org.appng.api.Platform;
import org.appng.api.ScheduledJob;
import org.appng.api.model.Application;
import org.appng.api.model.Site;
import org.appng.api.search.Consumer;
import org.appng.api.search.DocumentEvent;
import org.appng.api.search.DocumentProducer;
import org.appng.search.indexer.GlobalIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.Data;

/**
 * A {@link ScheduledJob} that builds to global search index for a {@link Site}.
 * 
 * @author Matthias Müller
 *
 * @see GlobalIndexer
 */
@Data
public class IndexJob implements ScheduledJob {

	private static final Logger LOG = LoggerFactory.getLogger(IndexJob.class);

	private Map<String, Object> jobDataMap;
	private String description;

	public void execute(Site site, Application application) throws Exception {
		LOG.debug("started IndexJob ({}) for site {}", description, site.getName());
		Consumer<DocumentEvent, DocumentProducer> indexer = application.getFeatureProvider().getIndexer();
		String jspFileType = (String) getJobDataMap().get(Platform.Property.JSP_FILE_TYPE);
		new GlobalIndexer(indexer).doIndex(site, jspFileType);
		LOG.debug("finished IndexJob ({}) for site {}", description, site.getName());
	}
}
