/*
 * Copyright 2011-2023 the original author or authors.
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

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.appng.api.FieldProcessor;
import org.appng.api.Request;
import org.appng.api.ScheduledJob;
import org.appng.api.model.Site;
import org.appng.application.scheduler.quartz.SchedulerJobDetail;
import org.appng.xml.platform.FieldDef;
import org.quartz.CronExpression;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.Trigger.TriggerState;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SchedulerUtils {

	public static final String JOB_SEPARATOR = "_";
	private Scheduler scheduler;
	private FieldProcessor fp;
	private Request request;

	public SchedulerUtils(Scheduler scheduler, FieldProcessor fp) {
		this(scheduler, fp, null);
	}

	public SchedulerUtils(Scheduler scheduler, FieldProcessor fp, Request request) {
		this.scheduler = scheduler;
		this.fp = fp;
		this.request = request;
	}

	public void addSimpleTrigger(JobDetail jobDetail, String id) throws SchedulerException {
		if (isRunning(jobDetail)) {
			addMessage(request, fp, MessageConstants.JOB_RUNNING_ERROR, true, false, "", id);
		} else {
			JobKey key = jobDetail.getKey();
			Trigger trigger = TriggerBuilder.newTrigger()
					.withIdentity(new TriggerKey(id + "-simpletrigger-" + hashCode(), jobDetail.getKey().getGroup()))
					.startNow().forJob(key).build();
			scheduler.scheduleJob(trigger);
			log.info("Created trigger '{}' for job '{}' with start time '{}'", trigger.getKey(),
					jobDetail.getJobDataMap().getString(Constants.JOB_SCHEDULED_JOB), trigger.getStartTime());
			addMessage(request, fp, MessageConstants.JOB_RUNNING, false, false, null, id);
		}
	}

	public void scheduleJob(JobDetail jobDetail, String id, String jobDesc, String triggerGroup)
			throws SchedulerException {
		List<? extends Trigger> triggersOfJob = getTriggers(jobDetail);
		if (0 == triggersOfJob.size()) {
			String cronExpression = jobDetail.getJobDataMap().getString(Constants.JOB_CRON_EXPRESSION);
			jobDetail.getJobDataMap().put(Constants.JOB_ENABLED, true);
			saveJob(jobDetail);
			addCronTrigger(jobDetail, cronExpression, id, jobDesc, triggerGroup);
		} else {
			addMessage(request, fp, MessageConstants.JOB_ACTIVE, true, false, null, id);
		}
	}

	public void rescheduleJob(JobDetail jobDetail, String cronExpression, String id, String jobDesc,
			String triggerGroup) throws SchedulerException {
		jobDetail.getJobDataMap().put(Constants.JOB_CRON_EXPRESSION, cronExpression);
		CronTrigger cronTrigger = getCronTrigger(jobDetail);
		if (cronTrigger != null) {
			cronTrigger = createCronTrigger(jobDetail, cronExpression, cronTrigger.getKey());
			scheduler.rescheduleJob(cronTrigger.getKey(), cronTrigger);
		}
		saveJob(jobDetail);
		addMessage(request, fp, MessageConstants.JOB_UPDATED, false, false, null, id);
	}

	protected CronTrigger getCronTrigger(JobDetail jobDetail) throws SchedulerException {
		CronTrigger cronTrigger = getTriggers(jobDetail).stream().filter(t -> (t instanceof CronTrigger))
				.map(CronTrigger.class::cast).findFirst().orElse(null);
		return cronTrigger;
	}

	protected List<? extends Trigger> getTriggers(JobDetail jobDetail) throws SchedulerException {
		return scheduler.getTriggersOfJob(jobDetail.getKey());
	}

	public boolean isRunning(JobDetail jobDetail) throws SchedulerException {
		List<TriggerKey> triggerKeys = getTriggers(jobDetail).stream().map(Trigger::getKey)
				.collect(Collectors.toList());
		for (TriggerKey triggerKey : triggerKeys) {
			if (TriggerState.COMPLETE.equals(scheduler.getTriggerState(triggerKey))) {
				return true;
			}
		}
		return false;
	}

	public boolean deleteCronTrigger(JobDetail jobDetail, String id, boolean forcefullyDisabled)
			throws SchedulerException {
		CronTrigger cronTrigger = getCronTrigger(jobDetail);
		if (null != cronTrigger) {
			String cronExpression = cronTrigger.getCronExpression();
			jobDetail.getJobDataMap().put(Constants.JOB_CRON_EXPRESSION, cronExpression);
			jobDetail.getJobDataMap().put(Constants.JOB_ENABLED, false);
			if (forcefullyDisabled) {
				jobDetail.getJobDataMap().put(Constants.JOB_FORCEFULLY_DISABLED, forcefullyDisabled);
				log.info("Job {} was disabled forcefully.", jobDetail.getKey());
			}
			saveJob(jobDetail);
			boolean unscheduled = scheduler.unscheduleJob(cronTrigger.getKey());
			if (unscheduled) {
				log.info("Deleted trigger '{}' for job '{}' with expression '{}'", cronTrigger.getKey(),
						jobDetail.getKey(), cronExpression);
				addMessage(request, fp, MessageConstants.JOB_UNSCHEDULED, false, false, null, id);
			}
			return unscheduled;
		}
		addMessage(request, fp, MessageConstants.JOB_NOT_EXISTS_ERROR, true, false, null, id);
		return false;
	}

	public void addCronTrigger(JobDetail jobDetail, String cronExpression, String id, String jobDesc,
			String triggerGroup) throws SchedulerException {
		CronTrigger cronTrigger = createCronTrigger(jobDetail, cronExpression, id, jobDesc, triggerGroup);
		if (cronTrigger != null) {
			scheduler.scheduleJob(cronTrigger);
			log.info("Created trigger '{}' for job '{}' with expression '{}'", cronTrigger.getKey(), jobDetail.getKey(),
					cronExpression);
			addMessage(request, fp, MessageConstants.JOB_SCHEDULED_EXPR, false, false, null, id, cronExpression);
		}
	}

	public CronTrigger createCronTrigger(JobDetail jobDetail, String cronExpression, String id, String jobDesc,
			String triggerGroup) {
		if (isValidExpression(cronExpression)) {
			TriggerKey triggerKey = new TriggerKey(id + "-crontrigger-" + hashCode(), triggerGroup);
			return createCronTrigger(jobDetail, cronExpression, triggerKey);
		}
		return null;
	}

	protected CronTrigger createCronTrigger(JobDetail jobDetail, String cronExpression, TriggerKey triggerKey) {
		return TriggerBuilder.newTrigger().withIdentity(triggerKey)
				.withSchedule(CronScheduleBuilder.cronSchedule(cronExpression)).forJob(jobDetail).build();
	}

	public boolean isValidExpression(String cronExpression) {
		if (null == cronExpression || !CronExpression.isValidExpression(cronExpression)) {
			addMessage(request, fp, MessageConstants.JOB_CRONEXPRESSION_INVALID, true, true, "jobModel.cronExpression");
			return false;
		}
		return true;
	}

	public void deleteJob(JobDetail jobDetail, String id) throws SchedulerException {
		JobKey key = jobDetail.getKey();
		if (!isRunning(jobDetail)) {
			scheduler.deleteJob(key);
			log.info("Deleted job: {}", jobDetail.getKey());
			addMessage(request, fp, MessageConstants.JOB_DELETED, false, false, null, id);
		} else {
			addMessage(request, fp, MessageConstants.JOB_DELETE_ERROR, true, false, null, id);
		}
	}

	public void addMessage(Request request, FieldProcessor fieldProcessor, String key, boolean isError,
			boolean attachToField, String fieldName, Object... args) {
		if (request != null) {
			String message = request.getMessage(key, args);
			if (isError) {
				if (attachToField) {
					if (!"".equals(fieldName) && fieldName != null) {
						FieldDef field = fieldProcessor.getField(fieldName);
						if (field != null) {
							fieldProcessor.addErrorMessage(field, message);
						} else {
							fieldProcessor.addErrorMessage(message);
						}
					}
				} else {
					fieldProcessor.addErrorMessage(message);
				}
			} else {
				fieldProcessor.addOkMessage(message);
			}
		}

	}

	public JobDetail getJobDetail(JobKey jobKey, Site site, String applicationName, ScheduledJob scheduledJob,
			String beanName) throws SchedulerException {
		JobDetail jobDetail = getJobDetail(jobKey);
		boolean isNewJob = null == jobDetail;
		if (isNewJob) {
			jobDetail = new SchedulerJobDetail(jobKey, scheduledJob.getDescription());
		}

		addMetaData(site, applicationName, scheduledJob, beanName, jobDetail);
		saveJob(jobDetail);
		return jobDetail;
	}

	public static void addMetaData(Site site, String applicationName, ScheduledJob scheduledJob, String beanName,
			JobDetail jobDetail) {
		JobDataMap persistentJobData = jobDetail.getJobDataMap();
		String cronExpression = persistentJobData.getString(Constants.JOB_CRON_EXPRESSION);
		boolean enabled = persistentJobData.getBoolean(Constants.JOB_ENABLED);

		persistentJobData.put(Constants.JOB_SCHEDULED_JOB, scheduledJob.getClass().getName());
		persistentJobData.put(Constants.JOB_ORIGIN, applicationName);
		persistentJobData.put(Constants.JOB_SITE_NAME, site.getName());
		persistentJobData.put(Constants.JOB_BEAN_NAME, beanName);
		if (null != scheduledJob.getJobDataMap()) {
			persistentJobData.putAll(scheduledJob.getJobDataMap());
		}
		if (persistentJobData.getBoolean(Constants.JOB_FORCEFULLY_DISABLED)) {
			enabled = true;
			persistentJobData.remove(Constants.JOB_FORCEFULLY_DISABLED);
			log.info("Job '{}' was disabled forcefully and is being reenabled.", jobDetail.getKey());
		}

		boolean forceState = persistentJobData.getBoolean(Constants.JOB_FORCE_STATE);
		if (StringUtils.isNotBlank(cronExpression) && !forceState) {
			persistentJobData.put(Constants.JOB_CRON_EXPRESSION, cronExpression);
			persistentJobData.put(Constants.JOB_ENABLED, enabled);
		}
	}

	public JobDetail getJobDetail(JobKey jobKey) throws SchedulerException {
		return scheduler.getJobDetail(jobKey);
	}

	public static JobKey getJobKey(String siteName, String applicationName, String jobBeanName) {
		return new JobKey(applicationName + JOB_SEPARATOR + jobBeanName, siteName);
	}

	public void saveJob(JobDetail jobDetail) throws SchedulerException {
		boolean isExisting = scheduler.checkExists(jobDetail.getKey());
		scheduler.addJob(jobDetail, true);
		if (!isExisting) {
			log.info("Created job: {}", jobDetail.getKey());
		} else {
			log.debug("Updated job: {}", jobDetail.getKey());
		}
	}

	public Set<JobKey> getJobsForSite(String siteName) throws SchedulerException {
		return scheduler.getJobKeys(GroupMatcher.jobGroupEquals(siteName));
	}

}
