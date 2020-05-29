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
package org.appng.application.scheduler.model;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.appng.api.ScheduledJob;
import org.appng.api.model.Application;
import org.appng.api.model.Named;
import org.appng.api.model.Site;
import org.appng.application.scheduler.Constants;
import org.appng.application.scheduler.MessageConstants;
import org.appng.application.scheduler.SchedulerUtils;
import org.quartz.CronTrigger;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.context.MessageSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Setter
@Slf4j
public class JobModel implements Named<String>, Comparable<Named<String>> {

	private String name;
	private String beanName;
	private String availableJob;
	private String description;
	private String origin;
	private String jobClass;
	private boolean running;
	private String cronExpression;
	private Date nextFireTime;
	private Date previousFireTime;
	private boolean beanAvailable;
	private String stateName;
	private String jobData;

	private static final ObjectWriter JSON_WRITER = new ObjectMapper().writerWithDefaultPrettyPrinter();

	public String getAvailableJob() {
		if (StringUtils.isBlank(availableJob)) {
			return name;
		} else {
			return availableJob;
		}
	}

	public boolean isScheduled() {
		return cronExpression != null;
	}

	public String getId() {
		return getName();
	}

	public int compareTo(Named<String> other) {
		return getName().compareTo(other.getName());
	}

	public static List<JobModel> getJobs(Scheduler scheduler, Site site, MessageSource messageSource, Locale locale)
			throws SchedulerException {
		Set<JobKey> jobKeys = scheduler.getJobKeys(GroupMatcher.jobGroupEquals(site.getName()));
		return jobKeys.stream().map(jk -> getJob(scheduler, messageSource, locale, jk.getName(), site))
				.filter(jm -> null != jm).sorted().collect(Collectors.toList());
	}

	public static JobModel getJob(Scheduler scheduler, MessageSource messageSource, Locale locale, String jobName,
			Site site) {
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
				jobModel.setBeanName(beanName);
				jobModel.setJobClass(jobClass);
				jobModel.setOrigin(origin);
				String jobDataJson = JSON_WRITER.writeValueAsString(new TreeMap<>(jobDataMap));
				jobModel.setJobData(jobDataJson);
				boolean beanAvailable = null != application
						&& null != application.getBean(beanName, ScheduledJob.class);
				jobModel.setBeanAvailable(beanAvailable);

				String stateKey = beanAvailable ? MessageConstants.JOB_STATE_AVAILABLE
						: MessageConstants.JOB_STATE_ERROR;

				List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobDetail.getKey());

				if (triggers.size() > 0) {
					for (Trigger trigger : triggers) {
						if (trigger instanceof CronTrigger) {
							CronTrigger cronTrigger = (CronTrigger) trigger;
							String cronExpression = cronTrigger.getCronExpression();
							Date previousFireTime = cronTrigger.getPreviousFireTime();
							Date nextFireTime = cronTrigger.getNextFireTime();
							jobModel.setCronExpression(cronExpression);
							jobModel.setPreviousFireTime(previousFireTime);
							jobModel.setNextFireTime(nextFireTime);
							stateKey = MessageConstants.JOB_STATE_SCHEDULED;
						} else {
							jobModel.setPreviousFireTime(trigger.getStartTime());
						}
					}
					if (new SchedulerUtils(scheduler, null).isRunning(jobDetail)) {
						jobModel.setRunning(true);
						stateKey = MessageConstants.JOB_STATE_RUNNING;
					}
				} else {
					String cronExpression = jobDataMap.getString(Constants.JOB_CRON_EXPRESSION);
					jobModel.setCronExpression(cronExpression);
				}
				String stateName = messageSource.getMessage(stateKey, new Object[0], locale);
				jobModel.setStateName(stateName);
			}
		} catch (SchedulerException | JsonProcessingException e) {
			log.error("error creating model for " + jobKey, e);
		}
		return jobModel;
	}

}
