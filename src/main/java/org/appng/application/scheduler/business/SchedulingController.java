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
package org.appng.application.scheduler.business;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.appng.api.ApplicationController;
import org.appng.api.Environment;
import org.appng.api.FieldProcessor;
import org.appng.api.ScheduledJob;
import org.appng.api.model.Application;
import org.appng.api.model.Site;
import org.appng.application.scheduler.Constants;
import org.appng.application.scheduler.PropertyConstants;
import org.appng.application.scheduler.SchedulerUtils;
import org.appng.application.scheduler.quartz.RecordingJobListener;
import org.appng.xml.platform.FieldDef;
import org.appng.xml.platform.Linkpanel;
import org.appng.xml.platform.Messages;
import org.appng.xml.platform.MetaData;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
public class SchedulingController extends SchedulerAware implements ApplicationController {

	private static final Logger LOGGER = LoggerFactory.getLogger(SchedulingController.class);

	private RecordingJobListener recordingJobListener;

	public SchedulingController(RecordingJobListener recordingJobListener, Scheduler scheduler) {
		this.recordingJobListener = recordingJobListener;
		this.scheduler = scheduler;
	}

	public boolean start(Site site, Application application, Environment env) {
		try {
			SchedulerUtils schedulerUtils = new SchedulerUtils(scheduler, getLoggingFieldProcessor());
			if (application.getProperties().getBoolean(PropertyConstants.VALIDATE_JOBS_ON_STARTUP)) {
				validateJobs(site, schedulerUtils);
			}

			for (Application a : site.getApplications()) {
				String[] jobBeanNames = a.getBeanNames(ScheduledJob.class);
				for (String jobBeanName : jobBeanNames) {
					ScheduledJob scheduledJob = (ScheduledJob) a.getBean(jobBeanName);
					if (null == scheduledJob) {
						LOGGER.warn("error retrieving {} from {}", jobBeanName, a.getName());
						continue;
					}
					try {
						JobKey jobKey = schedulerUtils.getJobKey(site.getName(), a.getName(), jobBeanName);
						JobDetail jobDetail = schedulerUtils.getJobDetail(jobKey, site, a.getName(), scheduledJob,
								jobBeanName);
						boolean enabled = jobDetail.getJobDataMap().getBoolean(Constants.JOB_ENABLED);
						if (enabled) {
							String description = scheduledJob.getDescription();
							schedulerUtils.scheduleJob(jobDetail, jobKey.getName(), description, site.getName());
						}
					} catch (Exception e) {
						LOGGER.error(String.format("error starting job '%s' of application %s (type is %s)",
								jobBeanName, application.getName(), scheduledJob.getClass().getName()), e);
					}
				}
			}
			scheduler.getListenerManager().addJobListener(recordingJobListener);
			scheduler.start();
		} catch (SchedulerException e) {
			LOGGER.error("error while starting scheduler", e);
			return false;
		}
		return true;
	}

	private void validateJobs(Site site, SchedulerUtils schedulerUtils) {
		try {
			for (JobKey jobKey : schedulerUtils.getJobsForSite(site.getName())) {
				boolean jobOK = false;
				JobDetail jobDetail = schedulerUtils.getJobDetail(jobKey);
				JobDataMap jobData = jobDetail.getJobDataMap();
				String appName = jobData.getString(Constants.JOB_ORIGIN);
				String beanName = jobData.getString(Constants.JOB_BEAN_NAME);
				if (StringUtils.isBlank(beanName)) {
					beanName = jobKey.getName().substring(appName.length() + 1);
					jobData.put(Constants.JOB_BEAN_NAME, beanName);
					schedulerUtils.saveJob(jobDetail);
				}
				Application app = site.getApplication(appName);
				if (null == app) {
					LOGGER.warn("application '{}' of site '{}' not found for job '{}'", appName, site.getName(),
							jobKey.getName());
				} else if (null == app.getBean(beanName, ScheduledJob.class)) {
					LOGGER.error("bean named '{}' not found in application '{}' of site '{}' for job '{}'", beanName,
							appName, site.getName(), jobKey.getName());
				} else {
					jobOK = true;
				}
				if (!jobOK && null != jobData.getString(Constants.JOB_CRON_EXPRESSION)) {
					schedulerUtils.deleteTrigger(jobDetail, jobKey.getName());
				}
			}
		} catch (SchedulerException e) {
			LOGGER.error("error while retrieving jobs for site " + site.getName(), e);
		}
	}

	public boolean removeSite(Site site, Application application, Environment environment) {
		try {
			scheduler.clear();
			scheduler.shutdown(false);
			scheduler.getContext().clear();
			return true;
		} catch (SchedulerException e) {
			LOGGER.error("error while removing scheduler from site " + site.getName(), e);
		} finally {
			scheduler = null;
		}
		return false;
	}

	/**
	 * Nothing to do here, since {@link org.quartz.Scheduler#shutdown(boolean)} is invoked by
	 * {@link org.springframework.scheduling.quartz.SchedulerFactoryBean#destroy()}
	 **/
	public boolean shutdown(Site site, Application application, Environment environment) {
		return true;
	}

	private FieldProcessor getLoggingFieldProcessor() {
		return new FieldProcessor() {

			public void addOkMessage(FieldDef field, String message) {
				addOkMessage(message);
			}

			public void addOkMessage(String message) {
				LOGGER.info(message);
			}

			public void addNoticeMessage(FieldDef field, String message) {
				addNoticeMessage(message);
			}

			public void addNoticeMessage(String message) {
				LOGGER.debug(message);
			}

			public void addInvalidMessage(FieldDef field, String message) {
				addInvalidMessage(message);
			}

			public void addInvalidMessage(String message) {
				LOGGER.warn(message);
			}

			public void addErrorMessage(FieldDef field, String message) {
				addErrorMessage(message);
			}

			public void addErrorMessage(String message) {
				LOGGER.error(message);
			}

			public List<FieldDef> getFields() {

				return null;
			}

			public FieldDef getField(String fieldBinding) {

				return null;
			}

			public MetaData getMetaData() {

				return null;
			}

			public boolean hasField(String fieldBinding) {

				return false;
			}

			public String getReference() {

				return null;
			}

			public boolean hasErrors() {

				return false;
			}

			public boolean hasFieldErrors() {

				return false;
			}

			public void addLinkPanels(List<Linkpanel> panels) {

			}

			public Linkpanel getLinkPanel(String fieldName) {

				return null;
			}

			public Messages getMessages() {

				return null;
			}

			public void clearMessages() {

			}

			public void clearFieldMessages() {

			}

			public void clearFieldMessages(String... fieldBindings) {

			}

			public Pageable getPageable() {
				return null;
			}

		};
	}

	public boolean addSite(Site site, Application application, Environment environment) {
		return true;
	}
}
