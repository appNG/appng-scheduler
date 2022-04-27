/*
 * Copyright 2011-2022 the original author or authors.
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

import java.util.HashMap;
import java.util.Map;

import org.appng.api.ScheduledJob;
import org.appng.api.model.Application;
import org.appng.api.model.Site;
import org.appng.application.scheduler.Constants;
import org.appng.application.scheduler.service.JobStateRestController.TimeUnit;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
public class LongRunningJob implements ScheduledJob {

	private Map<String, Object> jobDataMap = new HashMap<>();
	private String description;

	public LongRunningJob() {
		getJobDataMap().put(Constants.JOB_CRON_EXPRESSION, "0 0/10 * 1/1 * ? *");
		getJobDataMap().put(Constants.JOB_ENABLED, true);
		getJobDataMap().put(Constants.JOB_FORCE_STATE, true);
		getJobDataMap().put(Constants.THRESHOLD_ERROR, 5);
		getJobDataMap().put(Constants.THRESHOLD_TIMEUNIT, TimeUnit.DAY.name());
	}

	public void execute(Site site, Application application) throws Exception {
		int waited = 0;
		while (waited < 60) {
			Thread.sleep(1000);
			waited += 1;
		}
	}
}
