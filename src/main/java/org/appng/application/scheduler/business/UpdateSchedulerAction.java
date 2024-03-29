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

import org.appng.api.ActionProvider;
import org.appng.api.Environment;
import org.appng.api.FieldProcessor;
import org.appng.api.Options;
import org.appng.api.Request;
import org.appng.api.ScheduledJob;
import org.appng.api.model.Application;
import org.appng.api.model.Site;
import org.appng.application.scheduler.Constants;
import org.appng.application.scheduler.MessageConstants;
import org.appng.application.scheduler.SchedulerUtils;
import org.appng.application.scheduler.model.JobModel;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * An {@link ActionProvider} to update the parameters of a {@link ScheduledJob} , such as name, description and
 * execution time (cron-syntax).
 * 
 * @author Matthias Müller
 */
@Slf4j
@Component("updateJob")
public class UpdateSchedulerAction extends SchedulerAware implements ActionProvider<JobModel> {

	public UpdateSchedulerAction(Scheduler scheduler) {
		super(scheduler);
	}

	public void perform(Site site, Application application, Environment environment, Options options, Request request,
			JobModel valueHolder, FieldProcessor fp) {

		String siteName = site.getName();

		String id = options.getString(Constants.OPT_JOB, Constants.ATTR_ID);
		String cronExpn = valueHolder.getCronExpression();
		String jobDesc = valueHolder.getDescription();

		try {
			SchedulerUtils schedulerUtils = new SchedulerUtils(scheduler, fp, request);
			JobKey jobKey = new JobKey(id, siteName);
			JobDetail jobDetail = scheduler.getJobDetail(jobKey);
			if (cronExpn != null && !schedulerUtils.isValidExpression(cronExpn)) {
				return;
			}
			if (null != jobDetail && scheduler.checkExists(jobDetail.getKey())) {
				schedulerUtils.rescheduleJob(jobDetail, cronExpn, id, jobDesc, siteName);
			} else {
				String message = request.getMessage(MessageConstants.JOB_NOT_EXISTS_ERROR, id);
				fp.addErrorMessage(message);
			}
		} catch (SchedulerException e) {
			log.error("SchedulerException while updating scheduler", e);
		}
	}

}
