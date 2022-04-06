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
package org.appng.application.scheduler;

import java.util.HashMap;
import java.util.Map;

import org.appng.api.ScheduledJob;
import org.appng.api.model.Application;
import org.appng.api.model.Site;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public class SchedulerUtilsTest {

	@Test
	public void testAddMetadata() {
		ScheduledJob scheduledJob = new ScheduledJob() {

			@Override
			public Map<String, Object> getJobDataMap() {
				return null;
			}

			@Override
			public void setJobDataMap(Map<String, Object> map) {
			}

			@Override
			public String getDescription() {
				return null;
			}

			@Override
			public void setDescription(String description) {
			}

			@Override
			public void execute(Site site, Application application) throws Exception {
			}
		};

		Map<Object, Object> jobDataMap = new HashMap<>();
		jobDataMap.put(Constants.JOB_ENABLED, "false");
		jobDataMap.put(Constants.JOB_CRON_EXPRESSION, "0 30 4 ? * *");
		jobDataMap.put(Constants.JOB_FORCEFULLY_DISABLED, true);
		JobDetail jobDetail = JobBuilder.newJob().withIdentity("myjob", "mysite").ofType(TestJob.class)
				.setJobData(new JobDataMap(jobDataMap)).build();
		SchedulerUtils.addMetaData(Mockito.mock(Site.class), "myapp", scheduledJob, "myJob", jobDetail);

		Assert.assertEquals(true, jobDetail.getJobDataMap().getBoolean(Constants.JOB_ENABLED));
		Assert.assertEquals("0 30 4 ? * *", jobDetail.getJobDataMap().get(Constants.JOB_CRON_EXPRESSION));
	}

	class TestJob implements Job {

		@Override
		public void execute(JobExecutionContext context) throws JobExecutionException {

		}
	}

}
