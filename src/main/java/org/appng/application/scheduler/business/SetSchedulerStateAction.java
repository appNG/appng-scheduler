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
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * An {@link ActionProvider} to enable/disable a {@link ScheduledJob}.
 * 
 * @author Matthias Müller
 */
@Slf4j
@Component("setJobState")
public class SetSchedulerStateAction extends SchedulerAware implements ActionProvider<Void> {

	public SetSchedulerStateAction(Scheduler scheduler) {
		super(scheduler);
	}

	private static final String ACTION_DELETE = "delete";
	private static final String ACTION_START = "start";
	private static final String ACTION_UNSCHEDULE = "unschedule";
	private static final String ACTION_SCHEDULE = "schedule";

	public void perform(Site site, Application application, Environment environment, Options options, Request request,
			Void valueHolder, FieldProcessor fp) {
		String siteName = site.getName();

		String action = options.getString(Constants.OPT_JOB, Constants.ATTR_ACTION);
		String id = options.getString(Constants.OPT_JOB, Constants.ATTR_ID);
		try {
			SchedulerUtils schedulerUtils = new SchedulerUtils(scheduler, fp, request);

			JobKey jobKey = new JobKey(id, siteName);
			JobDetail jobDetail = scheduler.getJobDetail(jobKey);
			if (ACTION_SCHEDULE.equals(action)) {
				if (null == jobDetail) {
					fp.addErrorMessage(request.getMessage(MessageConstants.JOB_NOT_EXISTS_ERROR, id));
				} else {
					schedulerUtils.scheduleJob(jobDetail, id, "", siteName);
				}
			} else if (ACTION_UNSCHEDULE.equals(action)) {
				schedulerUtils.deleteCronTrigger(jobDetail, id, false);
			} else if (ACTION_START.equals(action)) {
				schedulerUtils.addSimpleTrigger(jobDetail, id);
			} else if (ACTION_DELETE.equals(action)) {
				schedulerUtils.deleteJob(jobDetail, id);
			}
		} catch (SchedulerException e) {
			log.error("Error while changing scheduler state", e);
		}
	}

}
