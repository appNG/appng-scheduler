/*
 * Copyright 2011-2022 the original author or authors.
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

import org.apache.commons.lang3.StringUtils;
import org.appng.api.ActionProvider;
import org.appng.api.Environment;
import org.appng.api.FieldProcessor;
import org.appng.api.Options;
import org.appng.api.Platform;
import org.appng.api.Request;
import org.appng.api.ScheduledJob;
import org.appng.api.Scope;
import org.appng.api.model.Application;
import org.appng.api.model.Properties;
import org.appng.api.model.Site;
import org.appng.application.scheduler.Constants;
import org.appng.application.scheduler.MessageConstants;
import org.appng.application.scheduler.SchedulerUtils;
import org.appng.application.scheduler.model.JobForm;
import org.appng.application.scheduler.model.JobModel;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * A {@link ActionProvider} to update the parameters of a {@link ScheduledJob}, such as name, description and execution
 * time (cron-syntax).
 * 
 * @author Matthias Müller
 */
@Slf4j
@Component("createJob")
public class CreateSchedulerAction extends SchedulerAware implements ActionProvider<JobForm> {

	public CreateSchedulerAction(Scheduler scheduler) {
		super(scheduler);
	}

	public void perform(Site site, Application application, Environment environment, Options options, Request request,
			JobForm valueHolder, FieldProcessor fp) {

		String message = null;
		try {

			JobModel jobModel = valueHolder.getJobModel();
			SchedulerUtils schedulerUtils = new SchedulerUtils(scheduler, fp, request);

			Properties appProps = environment.getAttribute(Scope.PLATFORM, Platform.Environment.PLATFORM_CONFIG);

			String locale = appProps.getString(Platform.Property.LOCALE);

			String jobName = jobModel.getAvailableJob();
			String[] splittedJobName = jobName.split(SchedulerUtils.JOB_SEPARATOR);
			String applicationName = splittedJobName[0];
			Application app = site.getApplication(applicationName);
			String beanName = splittedJobName[1];
			ScheduledJob scheduledJob = (ScheduledJob) app.getBean(beanName);
			JobKey jobKey = SchedulerUtils.getJobKey(site.getName(), applicationName, jobModel.getName());
			JobDetail jobDetail = schedulerUtils.getJobDetail(jobKey, site, applicationName, scheduledJob, beanName);

			if (StringUtils.isNotBlank(locale)) {
				jobDetail.getJobDataMap().put(Constants.JOB_LOCALE, locale);
			} else {
				jobDetail.getJobDataMap().put(Constants.JOB_LOCALE, "en");
			}

			schedulerUtils.addCronTrigger(jobDetail, jobModel.getCronExpression(), jobKey.getName(), "",
					jobKey.getGroup());
			message = request.getMessage(MessageConstants.JOB_CREATED, jobKey.getName());
		} catch (Exception e) {
			log.error("SchedulerException while creating scheduler", e);
		}
		if (!fp.hasErrors()) {
			fp.addOkMessage(message);
		}
	}
}
