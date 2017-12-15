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
package org.appng.application.scheduler.model;

import java.util.Set;

import org.appng.api.Environment;
import org.appng.api.FieldProcessor;
import org.appng.api.FormValidator;
import org.appng.api.Options;
import org.appng.api.Request;
import org.appng.api.model.Application;
import org.appng.api.model.Site;
import org.appng.application.scheduler.MessageConstants;
import org.appng.application.scheduler.SchedulerUtils;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobForm implements FormValidator {

	private static Logger log = LoggerFactory.getLogger(JobForm.class);
	private static String JOB_NAME = "jobModel.name";

	private JobModel jobModel;

	public JobForm() {
		this.jobModel = new JobModel();
	}

	public JobForm(JobModel jobModel) {
		this.jobModel = jobModel;
	}

	@Override
	public void validate(Site site, Application application, Environment environment, Options options, Request request,
			FieldProcessor fp) {
		try {
			Scheduler scheduler = application.getBean(Scheduler.class);
			Set<JobKey> jobKeys = scheduler.getJobKeys(GroupMatcher.jobGroupEquals(site.getName()));

			if (jobModel.getName() == null || "".equals(jobModel.getName())) {
				String message = request.getMessage(MessageConstants.JOB_NOT_NAME_ERROR);
				fp.addErrorMessage(fp.getField(JOB_NAME), message);
			} else {
				JobKey newKey = new JobKey(application.getName() + SchedulerUtils.JOB_SEPARATOR + jobModel.getName(),
						site.getName());
				if (jobKeys.contains(newKey)) {
					String message = request.getMessage(MessageConstants.JOB_NAME_EXISTS_ERROR, jobModel.getName());
					fp.addErrorMessage(fp.getField(JOB_NAME), message);
				}
			}

		} catch (SchedulerException e) {
			log.error("Error while validating name " + getJobModel().getName(), e);
		}
	}

	public JobModel getJobModel() {
		return jobModel;
	}

	public void setJobModel(JobModel jobModel) {
		this.jobModel = jobModel;
	}

}
