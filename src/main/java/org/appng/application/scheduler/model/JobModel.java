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

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.appng.api.ScheduledJob;
import org.appng.api.model.Application;
import org.appng.api.model.Named;
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

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JobModel implements Named<String>, Comparable<Named<String>> {

	private String name;
	private String availableJob;
	private String description;
	private String origin;
	private String jobClass;
	private boolean running;
	private String cronExpression;
	private Date nextFireTime;
	private Date previousFireTime;
	private boolean beanAvailable;

	public String getAvailableJob() {
		if (StringUtils.isBlank(availableJob)) {
			return name;
		} else {
			return availableJob;
		}
	}

	public void setAvailableJob(String availableJob) {
		this.availableJob = availableJob;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getOrigin() {
		return origin;
	}

	public void setOrigin(String origin) {
		this.origin = origin;
	}

	public String getJobClass() {
		return jobClass;
	}

	public void setJobClass(String jobClass) {
		this.jobClass = jobClass;
	}

	public boolean isRunning() {
		return running;
	}

	public void setRunning(boolean running) {
		this.running = running;
	}

	public String getCronExpression() {
		return cronExpression;
	}

	public void setCronExpression(String cronExpression) {
		this.cronExpression = cronExpression;
	}

	public Date getNextFireTime() {
		return nextFireTime;
	}

	public void setNextFireTime(Date nextFireTime) {
		this.nextFireTime = nextFireTime;
	}

	public Date getPreviousFireTime() {
		return previousFireTime;
	}

	public void setPreviousFireTime(Date previousFireTime) {
		this.previousFireTime = previousFireTime;
	}

	public boolean isBeanAvailable() {
		return beanAvailable;
	}

	public void setBeanAvailable(boolean beanAvailable) {
		this.beanAvailable = beanAvailable;
	}

	public String getId() {
		return getName();
	}

	public Date getVersion() {
		return null;
	}

	public int compareTo(Named<String> other) {
		return getName().compareTo(other.getName());
	}

	public static List<JobModel> getJobs(Scheduler scheduler, Site site) throws SchedulerException {
		Set<JobKey> jobKeys = scheduler.getJobKeys(GroupMatcher.jobGroupEquals(site.getName()));
		return jobKeys.stream().map(jk -> getJob(jk.getName(), scheduler, site)).filter(jm -> null != jm).sorted()
				.collect(Collectors.toList());
	}

	public static JobModel getJob(String jobName, Scheduler scheduler, Site site) {
		return null == jobName ? null : getModel(scheduler, jobName, site);
	}

	private static JobModel getModel(Scheduler scheduler, String jobName, Site site) {
		JobModel jobModel = null;
		JobKey jobKey = new JobKey(jobName, site.getName());
		try {
			JobDetail jobDetail = scheduler.getJobDetail(jobKey);
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
				boolean beanAvailable = null != application
						&& null != application.getBean(beanName, ScheduledJob.class);
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
		} catch (SchedulerException e) {
			log.error("error creating model for " + jobKey, e);
		}
		return jobModel;
	}

}
