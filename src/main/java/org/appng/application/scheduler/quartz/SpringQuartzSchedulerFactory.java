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
package org.appng.application.scheduler.quartz;

import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.ServletContext;

import org.appng.api.Environment;
import org.appng.api.Platform;
import org.appng.api.ScheduledJob;
import org.appng.api.Scope;
import org.appng.api.model.Properties;
import org.appng.api.model.Site;
import org.appng.api.support.environment.DefaultEnvironment;
import org.appng.application.scheduler.Constants;
import org.quartz.InterruptableJob;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.UnableToInterruptJobException;
import org.quartz.spi.JobFactory;
import org.quartz.spi.TriggerFiredBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.quartz.AdaptableJobFactory;
import org.springframework.web.context.WebApplicationContext;

/**
 * A {@link JobFactory} that creates Quartz {@link Job}s that are build from
 * {@link ScheduledJob}s.
 * 
 * @author Matthias Müller
 * @author Claus Stümke
 *
 */
public class SpringQuartzSchedulerFactory extends AdaptableJobFactory {

	private static final Logger LOGGER = LoggerFactory.getLogger(SpringQuartzSchedulerFactory.class);

	@Autowired
	private ApplicationContext applicationContext;

	private AtomicInteger id = new AtomicInteger(1);

	@Override
	protected Job createJobInstance(TriggerFiredBundle bundle) throws Exception {
		final JobDetail jobDetail = bundle.getJobDetail();
		final JobKey jobKey = jobDetail.getKey();
		LOGGER.debug("creating job instance for job {}", jobKey);

		org.appng.core.model.ApplicationContext appngAppContext = (org.appng.core.model.ApplicationContext) applicationContext;
		final Site site = appngAppContext.getSite();
		WebApplicationContext parentContext = (WebApplicationContext) appngAppContext.getParent();
		ServletContext servletContext = parentContext.getServletContext();
		final Environment environment = DefaultEnvironment.get(servletContext);

		final String eventId = hashCode() + "-" + id.getAndIncrement();
		if (jobDetail.getJobDataMap().getBoolean(Constants.JOB_HARD_INTERRUPTABLE)) {
			return getInterruptableJob(jobDetail, jobKey, site, environment, eventId);
		} else {
			return getJob(jobDetail, jobKey, site, environment, eventId);
		}
	}

	/**
	 * Returns a regular {@link Job} which cannot be interrupted
	 */
	private Job getJob(final JobDetail jobDetail, final JobKey jobKey, final Site site, final Environment environment,
			final String eventId) {
		return new Job() {
			public void execute(JobExecutionContext context) throws JobExecutionException {
				RunJobEvent runJobEvent = new RunJobEvent(eventId, jobKey, site.getName());
				try {
					runJobEvent.perform(environment, site);
					context.setResult(runJobEvent.getJobResult());
				} catch (Exception e) {
					context.setResult(runJobEvent.getJobResult());
					throw new JobExecutionException(e);
				}
				sendRunOnceEvent(jobDetail, jobKey, site, runJobEvent);
			}

		};
	}

	/**
	 * Returns an {@link InterruptableJob} that is is being interrupted by quartz
	 * when shutting down a {@link Scheduler} , e.g. on site reload. This is done by
	 * using a separate thread, which is interrupted when
	 * {@link InterruptableJob#interrupt()} is being called. It does not care about
	 * open resources, so use {@value #JOBDATA_HARD_INTERRUPTABLE}{@code =true} only
	 * for {@link ScheduledJob}s that can safely be stopped by interrupting its
	 * thread.
	 */
	private Job getInterruptableJob(final JobDetail jobDetail, final JobKey jobKey, final Site site,
			final Environment environment, final String eventId) {
		Properties platformProps = environment.getAttribute(Scope.PLATFORM, Platform.Environment.PLATFORM_CONFIG);
		// this is a 'secret' platform-property
		final Integer waitTime = platformProps.getInteger("interruptableWaitTime", 5000);

		return new InterruptableJob() {
			private Thread thread;

			public void execute(JobExecutionContext context) throws JobExecutionException {
				final RunJobEvent runJobEvent = new RunJobEvent(eventId, jobKey, site.getName());
				Runnable runnable = new Runnable() {
					@Override
					public void run() {
						try {
							runJobEvent.perform(environment, site);
							context.setResult(runJobEvent.getJobResult());
						} catch (Exception e) {
							context.setResult(runJobEvent.getJobResult());
							LOGGER.error("Caught Exception on job execution: ", e);
						}
						sendRunOnceEvent(jobDetail, jobKey, site, runJobEvent);
					}
				};
				// run the job in a separate thread so it can be interrupted.
				thread = new Thread(runnable, jobKey.getName());
				thread.start();
				boolean keepGoing = true;
				while (keepGoing) {
					try {
						Thread.sleep(waitTime);
					} catch (InterruptedException e) {
						LOGGER.info("Thread " + thread.getName() + " was interrupted while sleeping");
					}
					keepGoing = (thread.isAlive() && !thread.isInterrupted());
				}
			}

			public void interrupt() throws UnableToInterruptJobException {
				thread.interrupt();
			}
		};
	}

	private void sendRunOnceEvent(final JobDetail jobDetail, final JobKey jobKey, final Site site,
			RunJobEvent runJobEvent) {
		if (jobDetail.getJobDataMap().getBoolean(Constants.JOB_RUN_ONCE)) {
			LOGGER.info("job {} has option '{}' set, no event will be send.", jobKey, Constants.JOB_RUN_ONCE);
		} else {
			site.sendEvent(runJobEvent);
			LOGGER.info("sending {}", runJobEvent);
		}
	}
}
