/*
 * Copyright 2011-2018 the original author or authors.
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
package org.appng.application.scheduler.quartz;

import java.util.Date;

import org.appng.application.scheduler.model.JobResult;
import org.appng.application.scheduler.service.JobRecordService;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * A {@link JobListener} to be added to the {@link Scheduler} to get triggered when a job has ended. It saves some data
 * about the job in a database. Such as start- and endtime, runtime and result.
 * 
 * @author Claus Stümke
 *
 */
@Component
public class RecordingJobListener implements JobListener {

	private JobRecordService jobRecordService;

	@Value("${enableJobRecord:true}")
	private boolean enabled;

	public RecordingJobListener(JobRecordService jobRecordService) {
		this.jobRecordService = jobRecordService;
	}

	@Override
	public String getName() {
		return this.getClass().getName();
	}

	@Override
	public void jobToBeExecuted(JobExecutionContext context) {
		// we are not interested on jobs to e executed we just want to get triggered when the job is done
	}

	@Override
	public void jobExecutionVetoed(JobExecutionContext context) {
		// we are not interested on jobs to e executed we just want to get triggered when the job is done
	}

	@Override
	public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
		if (enabled) {
			Object result = context.getResult();
			if (result instanceof JobResult) {
				jobRecordService.recordJob((JobResult) result, context.getFireTime(), new Date(),
						context.getJobRunTime(), context.getJobDetail().getJobDataMap(), jobException,
						context.getTrigger().getJobKey().getName());
			}
		}
	}

}
