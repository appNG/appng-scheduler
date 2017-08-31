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
package org.appng.application.scheduler.quartz;

import org.appng.api.BusinessException;
import org.appng.api.Environment;
import org.appng.api.InvalidConfigurationException;
import org.appng.api.ScheduledJob;
import org.appng.api.messaging.Event;
import org.appng.api.model.Application;
import org.appng.api.model.Site;
import org.appng.application.scheduler.Constants;
import org.appng.application.scheduler.SchedulerUtils;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StopWatch;

public class RunJobEvent extends Event {

	private static final long serialVersionUID = 3439557609209638189L;
	private static final String SCHEDULER_APPLICATION = "schedulerApplication";
	private String id;
	private JobKey jobKey;

	public RunJobEvent(String id, JobKey jobKey, String siteName) {
		super(siteName);
		this.id = id;
		this.jobKey = jobKey;
	}

	public void perform(Environment environment, Site site) throws InvalidConfigurationException, BusinessException {
		Logger logger = LoggerFactory.getLogger(RunJobEvent.class);
		String appName = null;
		try {
			String schedulerAppName = site.getProperties().getString(SCHEDULER_APPLICATION, "appng-scheduler");
			Application appngScheduler = site.getApplication(schedulerAppName);
			Scheduler scheduler = appngScheduler.getBean(Scheduler.class);
			JobDetail jobDetail = scheduler.getJobDetail(jobKey);
			appName = jobDetail.getJobDataMap().getString(Constants.JOB_ORIGIN);

			Application application = site.getApplication(appName);
			String jobName = jobKey.getName();
			String beanName = jobName.substring(appName.length() + SchedulerUtils.JOB_SEPARATOR.length());
			ScheduledJob job = (ScheduledJob) application.getBean(beanName);
			if (null == job) {
				throw new BusinessException("ScheduledJob " + beanName + " not found in application " + appName);
			}
			job.setJobDataMap(jobDetail.getJobDataMap());

			StopWatch sw = new StopWatch();
			sw.start();
			job.execute(site, application);
			sw.stop();
			Object[] args = new Object[] { jobKey, appName, site.getName(), sw.getTotalTimeMillis() };
			logger.debug("executing job {} for application {} in site {} took {}ms", args);

		} catch (SchedulerException e) {
			throw new BusinessException(e);
		} catch (Exception e) {
			Object[] args = new Object[] { jobKey, appName, site.getName() };
			logger.warn("error while executing job {} for application {} in site {} ", args);
			if (e instanceof BusinessException) {
				throw (BusinessException) e;
			}
			throw new BusinessException(e);
		}
	}

	@Override
	public String toString() {
		return getClass().getName() + "#" + id + "[site: " + getSiteName() + ", job: " + jobKey + "]";
	}

}
