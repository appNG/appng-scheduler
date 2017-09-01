/*
 * Copyright 2011-2017 the original author or authors.
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

import org.appng.api.ApplicationController;
import org.appng.api.Environment;
import org.appng.api.FieldProcessor;
import org.appng.api.ScheduledJob;
import org.appng.api.model.Application;
import org.appng.api.model.Site;
import org.appng.application.scheduler.Constants;
import org.appng.application.scheduler.SchedulerUtils;
import org.appng.xml.platform.FieldDef;
import org.appng.xml.platform.Linkpanel;
import org.appng.xml.platform.Messages;
import org.appng.xml.platform.MetaData;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;

public class SchedulingController extends SchedulerAware implements ApplicationController {

	private static final Logger LOGGER = LoggerFactory.getLogger(SchedulingController.class);

	public boolean start(Site site, Application application, Environment env) {
		try {
			SchedulerUtils schedulerUtils = new SchedulerUtils(scheduler, getLoggingFieldProcessor());

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
						JobDetail jobDetail = schedulerUtils.getJobDetail(jobKey, site, a.getName(), scheduledJob);
						boolean isNewJob = !scheduler.checkExists(jobKey);

						boolean enabled = "true"
								.equalsIgnoreCase((String) jobDetail.getJobDataMap().get(Constants.JOB_ENABLED));

						if (isNewJob && enabled) {
							String cronExpression = (String) jobDetail.getJobDataMap()
									.get(Constants.JOB_CRON_EXPRESSION);
							String description = scheduledJob.getDescription();
							schedulerUtils.addJob(jobDetail, description, cronExpression);
						} else {
							scheduler.addJob(jobDetail, true);
						}
					} catch (Exception e) {
						LOGGER.error("error starting job '" + jobBeanName + "' of application " + application.getName()
								+ " (type is" + scheduledJob.getClass().getName() + ")", e);
					}
				}
			}
			scheduler.start();
		} catch (SchedulerException e) {
			LOGGER.error("error while starting scheduler", e);
			return false;
		}
		return true;
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
