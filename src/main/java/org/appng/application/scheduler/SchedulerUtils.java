/*
 * Copyright 2015 the original author or authors.
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

import org.apache.commons.lang3.StringUtils;
import org.appng.api.FieldProcessor;
import org.appng.api.Request;
import org.appng.api.ScheduledJob;
import org.appng.api.model.Site;
import org.appng.application.scheduler.message.MessageConstants;
import org.appng.xml.platform.FieldDef;
import org.quartz.CronExpression;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;

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
			Trigger trigger = TriggerBuilder.newTrigger().withIdentity("simpletrigger-" + hashCode(), key.getGroup())
					.startNow().forJob(key).build();
			scheduler.scheduleJob(trigger);
			addMessage(request, fp, MessageConstants.JOB_RUNNING, false, false, null, id);
		}
	}

	public void scheduleJob(JobDetail jobDetail, String cronExpression, String id, String jobDesc, String triggerGroup)
			throws SchedulerException {
		List<? extends Trigger> triggersOfJob;
		triggersOfJob = scheduler.getTriggersOfJob(jobDetail.getKey());
		if (0 == triggersOfJob.size()) {
			addCronTrigger(jobDetail, cronExpression, id, jobDesc, triggerGroup);
		} else {
			addMessage(request, fp, MessageConstants.JOB_ACTIVE, true, false, null, id);
		}
	}

	public void rescheduleJob(JobDetail jobDetail, String cronExpression, String id, String jobDesc, String triggerGroup)
			throws SchedulerException {
		TriggerKey triggerKey = getTriggerKey(jobDetail, id);
		jobDetail.getJobDataMap().put(Constants.JOB_CRON_EXPRESSION, cronExpression);
		if (triggerKey != null) {
			CronTrigger cronTrigger = getCronTrigger(jobDetail, cronExpression, id, jobDesc, triggerGroup);
			if (cronTrigger != null) {
				scheduler.rescheduleJob(triggerKey, cronTrigger);
				addMessage(request, fp, MessageConstants.JOB_UPDATED, false, false, null, id);
				return;
			} else {
				addMessage(request, fp, MessageConstants.JOB_UPDATE_ERROR, true, false, null, id);
				return;
			}
		} else {
			scheduler.addJob(jobDetail, true);
			addMessage(request, fp, MessageConstants.JOB_UPDATED, false, false, null, id);
		}
	}

	public boolean isRunning(JobDetail jobDetail) throws SchedulerException {
		for (JobExecutionContext job : scheduler.getCurrentlyExecutingJobs()) {
			if (jobDetail.getKey().equals(job.getJobDetail().getKey())) {
				return true;
			}
		}
		return false;
	}

	public void deleteTrigger(JobDetail jobDetail, String id) throws SchedulerException {
		List<? extends Trigger> triggersOfJob = scheduler.getTriggersOfJob(jobDetail.getKey());
		if (0 == triggersOfJob.size()) {
			fp.addErrorMessage(request.getMessage(MessageConstants.JOB_NOT_EXISTS_ERROR, id));
		} else {
			CronTrigger cronTrigger = (CronTrigger) triggersOfJob.get(0);
			String exp = cronTrigger.getCronExpression();
			jobDetail.getJobDataMap().put(Constants.JOB_CRON_EXPRESSION, exp);
			// replace the job with new cronExpression
			scheduler.addJob(jobDetail, true);
			// unschedule the job i.e. Delete the Trigger.
			scheduler.unscheduleJob(triggersOfJob.get(0).getKey());
			addMessage(request, fp, MessageConstants.JOB_UNSCHEDULED, false, false, null, id);
		}
	}

	public TriggerKey getTriggerKey(JobDetail jobDetail, String id) throws SchedulerException {
		List<? extends Trigger> triggersOfJob = scheduler.getTriggersOfJob(jobDetail.getKey());
		if (triggersOfJob.size() > 0) {
			return triggersOfJob.get(0).getKey();
		}
		return null;
	}

	public void addCronTrigger(JobDetail jobDetail, String cronExpression, String id, String jobDesc,
			String triggerGroup) throws SchedulerException {
		if (isValidExpression(cronExpression)) {
			CronTrigger cronTrigger = getCronTrigger(jobDetail, cronExpression, id, jobDesc, triggerGroup);
			if (cronTrigger != null) {
				scheduler.scheduleJob(cronTrigger);
				addMessage(request, fp, MessageConstants.JOB_SCHEDULED_EXPR, false, false, null, id, cronExpression);
			}
		}
	}

	public CronTrigger getCronTrigger(JobDetail jobDetail, String cronExpression, String id, String jobDesc,
			String triggerGroup) {
		if (isValidExpression(cronExpression)) {
			TriggerKey triggerKey = new TriggerKey(id + "-crontrigger-" + hashCode(), triggerGroup);
			CronTrigger cronTrigger = TriggerBuilder.newTrigger().withIdentity(triggerKey)
					.withSchedule(CronScheduleBuilder.cronSchedule(cronExpression)).forJob(jobDetail).build();
			return cronTrigger;
		}
		return null;
	}

	public boolean isValidExpression(String cronExpression) {
		if (null == cronExpression || !CronExpression.isValidExpression(cronExpression)) {
			addMessage(request, fp, MessageConstants.CRONEXPRESSION_INVALID, true, true, "jobModel.cronExpression");
			return false;
		}
		return true;
	}

	public void deleteJob(JobDetail jobDetail, String id) throws SchedulerException {
		JobKey key = jobDetail.getKey();
		if (!isRunning(jobDetail)) {
			scheduler.deleteJob(key);
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

	public JobDetail getJobDetail(JobKey jobKey, Site site, String applicationName, ScheduledJob scheduledJob)
			throws SchedulerException {
		JobDetail jobDetail = scheduler.getJobDetail(jobKey);
		if (null == jobDetail) {
			JobBuilder jobbuilder = JobBuilder.newJob(Job.class).withIdentity(jobKey).storeDurably();
			jobDetail = jobbuilder.build();
		} else {
			jobDetail.getJobDataMap().clear();
		}
		JobDataMap jobDataMap = jobDetail.getJobDataMap();
		jobDataMap.put(Constants.JOB_SCHEDULED_JOB, scheduledJob.getClass().getName());
		jobDataMap.put(Constants.JOB_ORIGIN, applicationName);
		jobDataMap.put(Constants.JOB_SITE_NAME, site.getName());
		if (null != scheduledJob.getJobDataMap()) {
			jobDataMap.putAll(scheduledJob.getJobDataMap());
		}
		return jobDetail;
	}

	public JobKey getJobKey(String siteName, String applicationName, String jobBeanName) {
		return new JobKey(applicationName + JOB_SEPARATOR + jobBeanName, siteName);
	}

	public void addJob(JobDetail jobDetail, String description, String cronExpression) throws SchedulerException {
		scheduler.addJob(jobDetail, true);
		if (StringUtils.isNotBlank(description)) {
			jobDetail.getJobDataMap().put(Constants.JOB_DESCRIPTION, description);
		}
		JobKey jobKey = jobDetail.getKey();
		addCronTrigger(jobDetail, cronExpression, jobKey.getName(), "", jobKey.getGroup());
	}

}
