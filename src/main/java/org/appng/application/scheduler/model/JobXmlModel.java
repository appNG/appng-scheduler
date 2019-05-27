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
package org.appng.application.scheduler.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.appng.api.ScheduledJob;
import org.appng.api.model.Application;
import org.appng.api.model.Site;
import org.appng.application.scheduler.Constants;
import org.appng.application.scheduler.SchedulerUtils;
import org.quartz.CronTrigger;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.impl.matchers.GroupMatcher;

public class JobXmlModel {

	public List<JobModel> getJobs(Scheduler scheduler, Site site) throws SchedulerException {
		List<JobModel> list = new ArrayList<JobModel>();
		Set<JobKey> jobKeys = scheduler.getJobKeys(GroupMatcher.jobGroupEquals(site.getName()));
		for (JobKey jobKey : jobKeys) {
			JobModel job = getJob(jobKey.getName(), scheduler, site);
			list.add(job);
		}
		Collections.sort(list);
		return list;
	}

	public JobModel getJob(String jobName, Scheduler scheduler, Site site) throws SchedulerException {
		if (null != jobName) {
			return getModel(scheduler, jobName, site);
		}
		return null;
	}

	private JobModel getModel(Scheduler scheduler, String jobName, Site site) throws SchedulerException {
		JobModel jobModel = null;
		JobDetail jobDetail = scheduler.getJobDetail(new JobKey(jobName, site.getName()));
		if (null != jobDetail) {
			JobDataMap jobDataMap = jobDetail.getJobDataMap();
			String jobClass = jobDataMap.getString(Constants.JOB_SCHEDULED_JOB);
			String origin = jobDataMap.getString(Constants.JOB_ORIGIN);
			String beanName = jobDataMap.getString(Constants.JOB_BEAN_NAME);
			Application application = site.getApplication(origin);
			jobModel = new JobModel();
			jobModel.setName(jobName);
			jobModel.setJobClass(jobClass);
			jobModel.setOrigin(origin);
			boolean beanAvailable = null != application && null != application.getBean(beanName, ScheduledJob.class);
			jobModel.setBeanAvailable(beanAvailable);

			List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobDetail.getKey());
			// job has a Trigger
			if (triggers.size() > 0) {
				for (Trigger trigger : triggers) {
					// It is a cron trigger
					if (trigger instanceof CronTrigger) {
						CronTrigger cronTrigger = (CronTrigger) trigger;
						String cronExpression = cronTrigger.getCronExpression();
						Date previousFireTime = cronTrigger.getPreviousFireTime();
						Date nextFireTime = cronTrigger.getNextFireTime();
						jobModel.setCronExpression(cronExpression);
						jobModel.setPreviousFireTime(previousFireTime);
						jobModel.setNextFireTime(nextFireTime);

					}
				}
				if (new SchedulerUtils(scheduler, null).isRunning(jobDetail)) {
					jobModel.setRunning(true);
				}
			} else {
				String cronExpression = (String) jobDataMap.get(Constants.JOB_CRON_EXPRESSION);
				jobModel.setCronExpression(cronExpression);
			}
		}
		return jobModel;
	}

}
