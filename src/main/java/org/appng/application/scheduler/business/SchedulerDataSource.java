/*
 * Copyright 2011-2023 the original author or authors.
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

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.appng.api.ApplicationException;
import org.appng.api.DataContainer;
import org.appng.api.DataProvider;
import org.appng.api.Environment;
import org.appng.api.FieldProcessor;
import org.appng.api.Options;
import org.appng.api.Request;
import org.appng.api.ScheduledJob;
import org.appng.api.model.Application;
import org.appng.api.model.Named;
import org.appng.api.model.Site;
import org.appng.api.support.OptionGroupFactory;
import org.appng.api.support.OptionGroupFactory.OptionGroup;
import org.appng.application.scheduler.Constants;
import org.appng.application.scheduler.SchedulerUtils;
import org.appng.application.scheduler.model.JobForm;
import org.appng.application.scheduler.model.JobModel;
import org.appng.xml.platform.Label;
import org.appng.xml.platform.Selection;
import org.appng.xml.platform.SelectionType;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * A {@link DataProvider} which returns informations about all implementations of {@link ScheduledJob} that where
 * registered during application startup
 * 
 * @author Matthias Müller
 */
@Component("jobs")
public class SchedulerDataSource extends SchedulerAware implements DataProvider {

	private static final String ACTION_CREATE = "create";
	private static final String ACTION_DELETE = "delete";
	private static final String AVAILABLE_JOB = "availableJob";

	private final MessageSource messageSource;

	public SchedulerDataSource(Scheduler scheduler, MessageSource messageSource) {
		super(scheduler);
		this.messageSource = messageSource;
	}

	public DataContainer getData(Site site, Application application, Environment env, Options options, Request request,
			FieldProcessor fp) {
		DataContainer data = new DataContainer(fp);

		String jobId = options.getOptionValue(Constants.OPT_JOB, Constants.ATTR_ID);
		String actionId = options.getOptionValue(Constants.OPT_ACTION, Constants.ATTR_ID);
		String actionForm = request.getParameter(Constants.FORM_ACTION);
		try {
			if (StringUtils.isNotBlank(jobId) && !ACTION_DELETE.equals(actionForm)) {
				JobModel job = JobModel.getJob(scheduler, messageSource, env.getLocale(), jobId, site);
				data.setItem(job);
			} else {
				if (ACTION_CREATE.equals(actionId)) {
					JobModel jobModel = new JobModel();
					jobModel.setName(StringUtils.EMPTY);
					jobModel.setDescription(StringUtils.EMPTY);
					jobModel.setCronExpression(StringUtils.EMPTY);
					JobForm jobForm = new JobForm(jobModel);
					data.setItem(jobForm);

					Selection selection = new Selection();
					selection.setId(AVAILABLE_JOB);
					selection.setType(SelectionType.SELECT);
					Label label = new Label();
					label.setId(AVAILABLE_JOB);
					selection.setTitle(label);

					OptionGroupFactory optionGroupFactory = new OptionGroupFactory();
					for (Application a : site.getApplications()) {
						String[] jobClasses = a.getBeanNames(ScheduledJob.class);
						if (jobClasses.length > 0) {
							List<Named<String>> jobs = new ArrayList<Named<String>>();
							for (int i = 0; i < jobClasses.length; i++) {
								jobs.add(new NamedJob(jobClasses[i],
										a.getName() + SchedulerUtils.JOB_SEPARATOR + jobClasses[i]));
							}
							OptionGroup applicationJobs = optionGroupFactory.fromNamed(a.getName(), a.getName(), jobs,
									(Named<String>) null);
							selection.getOptionGroups().add(applicationJobs);
						}
					}
					data.getSelections().add(selection);
				} else {
					List<JobModel> jobs = JobModel.getJobs(scheduler, site, messageSource, env.getLocale());
					data.setPage(jobs, fp.getPageable());
				}
			}
		} catch (SchedulerException e) {
			throw new ApplicationException("error while retrieving job(s)", e);
		}

		return data;
	}

	@Getter
	@AllArgsConstructor
	class NamedJob implements Named<String> {
		private String name;
		private String id;

		public String getDescription() {
			return null;
		}
	}
}
