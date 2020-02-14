/*
 * Copyright 2011-2020 the original author or authors.
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

import org.appng.api.ScheduledJob;
import org.appng.api.ScheduledJobResult;
import org.appng.api.ScheduledJobResult.ExecutionResult;
import org.appng.api.model.Application;
import org.appng.api.model.Site;
import org.appng.application.scheduler.service.JobRecordService;

import lombok.Data;

/**
 * A {@link ScheduledJob} to remove old job records from the database.
 * 
 * @author Claus Stümke
 */
@Data
public class JobRecordHouseKeepingJob implements ScheduledJob {

	private JobRecordService jobRecordService;

	private ScheduledJobResult result;

	private Map<String, Object> jobDataMap;

	private String description;

	public void execute(Site site, Application application) throws Exception {
		Integer deleted = jobRecordService.cleanUp(site, application);
		this.result = new ScheduledJobResult();
		this.result.setResult(ExecutionResult.SUCCESS);
		this.result.setCustomData(String.format("%s records have been deleted for site %s", deleted, site.getName()));
	}

}
