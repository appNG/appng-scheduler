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
package org.appng.application.scheduler.quartz;

import org.appng.application.scheduler.Constants;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;

/**
 * A custom {@link JobDetail} implementation that does not rely on {@link DisallowConcurrentExecution} in
 * {@link #isConcurrentExectionDisallowed()}. Instead, the value of {@value Constants#JOB_ALLOW_CONCURRENT_EXECUTIONS}
 * from the job's datamap is used. This is a workaround for
 * <a href="https://github.com/quartz-scheduler/quartz/issues/184">Quartz issue #184</a>.
 * 
 * @author Matthias Müller
 *
 */
public class SchedulerJobDetail implements JobDetail {

	private JobDetail detail;

	public SchedulerJobDetail(JobKey key, String description) {
		JobBuilder builder = JobBuilder.newJob(Job.class).withIdentity(key).withDescription(description)
				.requestRecovery(false).storeDurably(true);
		this.detail = builder.build();
	}

	public SchedulerJobDetail(JobDetail detail) {
		this.detail = detail;
	}

	public boolean isConcurrentExectionDisallowed() {
		return !getJobDataMap().getBoolean(Constants.JOB_ALLOW_CONCURRENT_EXECUTIONS);
	}

	// delegate methods
	public JobKey getKey() {
		return detail.getKey();
	}

	public String getDescription() {
		return detail.getDescription();
	}

	public Class<? extends Job> getJobClass() {
		return detail.getJobClass();
	}

	public JobDataMap getJobDataMap() {
		return detail.getJobDataMap();
	}

	public boolean isDurable() {
		return detail.isDurable();
	}

	public boolean isPersistJobDataAfterExecution() {
		return detail.isPersistJobDataAfterExecution();
	}

	public boolean requestsRecovery() {
		return detail.requestsRecovery();
	}

	public Object clone() {
		return new SchedulerJobDetail((JobDetail) detail.clone());
	}

	public JobBuilder getJobBuilder() {
		return detail.getJobBuilder();
	}

	@Override
	public String toString() {
		return detail.toString();
	}

	@Override
	public boolean equals(Object obj) {
		return detail.equals(obj);
	}

	@Override
	public int hashCode() {
		return detail.hashCode();
	}

}
