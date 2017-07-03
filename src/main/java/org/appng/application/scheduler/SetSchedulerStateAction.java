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

import org.apache.log4j.Logger;
import org.appng.api.ActionProvider;
import org.appng.api.Environment;
import org.appng.api.FieldProcessor;
import org.appng.api.Options;
import org.appng.api.Request;
import org.appng.api.ScheduledJob;
import org.appng.api.model.Application;
import org.appng.api.model.Site;
import org.appng.application.scheduler.message.MessageConstants;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.SchedulerException;

/**
 * An {@link ActionProvider} to enable/disable a {@link ScheduledJob}.
 * 
 * @author Matthias Müller
 * 
 */
public class SetSchedulerStateAction extends SchedulerAware implements ActionProvider<Void> {

	private static final String ACTION_DELETE = "delete";
	private static final String ACTION_START = "start";
	private static final String ACTION_UNSCHEDULE = "unschedule";
	private static final String ACTION_SCHEDULE = "schedule";
	private static Logger log = Logger.getLogger(SetSchedulerStateAction.class);

	public void perform(Site site, Application application, Environment environment, Options options, Request request,
			Void valueHolder, FieldProcessor fp) {
		String siteName = site.getName();

		String action = options.getOption(Constants.OPT_JOB).getAttribute(Constants.ATTR_ACTION);
		String id = options.getOption(Constants.OPT_JOB).getAttribute(Constants.ATTR_ID);
		try {
			SchedulerUtils schedulerUtils = new SchedulerUtils(scheduler, fp, request);

			JobKey jobKey = new JobKey(id, siteName);
			JobDetail jobDetail = scheduler.getJobDetail(jobKey);
			if (ACTION_SCHEDULE.equals(action)) {
				scheduler.getJobDetail(jobKey);
				if (null == jobDetail) {
					fp.addErrorMessage(request.getMessage(MessageConstants.JOB_NOT_EXISTS_ERROR, id));
				} else {
					String cronExpression = (String) jobDetail.getJobDataMap().get(Constants.JOB_CRON_EXPRESSION);
					schedulerUtils.scheduleJob(jobDetail, cronExpression, id, "", siteName);
				}
			} else if (ACTION_UNSCHEDULE.equals(action)) {
				schedulerUtils.deleteTrigger(jobDetail, id);
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
