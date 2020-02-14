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
package org.appng.application.scheduler;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.spi.JobFactory;
import org.quartz.spi.TriggerFiredBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestJobFactory implements JobFactory {

	private static final Logger LOGGER = LoggerFactory.getLogger(TestJobFactory.class);

	public Job newJob(TriggerFiredBundle bundle, Scheduler scheduler) throws SchedulerException {
		Job quartzJob = new Job() {
			public void execute(JobExecutionContext context) throws JobExecutionException {
				try {
					LOGGER.info("executing " + context.getJobDetail().getKey());
				} catch (Exception e) {
					throw new JobExecutionException(e);
				}
			}
		};
		return quartzJob;
	}

}
