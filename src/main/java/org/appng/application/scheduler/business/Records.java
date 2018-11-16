/*
 * Copyright 2011-2018 the original author or authors.
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

import org.appng.api.DataContainer;
import org.appng.api.DataProvider;
import org.appng.api.Environment;
import org.appng.api.FieldProcessor;
import org.appng.api.Options;
import org.appng.api.Request;
import org.appng.api.ScheduledJobResult.ExecutionResult;
import org.appng.api.model.Application;
import org.appng.api.model.Site;
import org.appng.api.support.SelectionBuilder;
import org.appng.api.support.SelectionFactory;
import org.appng.application.scheduler.MessageConstants;
import org.appng.application.scheduler.model.JobRecord;
import org.appng.application.scheduler.service.JobRecordService;
import org.appng.xml.platform.Selection;
import org.appng.xml.platform.SelectionGroup;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;

/**
 * A {@link DataProvider} for recorded job executions. It supports some filters.
 * 
 * @author Claus Stümke
 *
 */

@Component
public class Records implements DataProvider {

	private static final String START_FILTER_DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm";
	private static final String APPLICATION_FILTER = "ap";
	private static final String JOB_FILTER = "job";
	private static final String START_AFTER_FILTER = "sa";
	private static final String START_BEFORE_FILTER = "sb";
	private static final String MIN_DURATION_FILTER = "du";
	private static final String RESULT_FILTER = "re";

	private JobRecordService jobRecordService;

	public Records(JobRecordService jobRecordService) {
		this.jobRecordService = jobRecordService;
	}

	@Override
	public DataContainer getData(Site site, Application application, Environment environment, Options options,
			Request request, FieldProcessor fieldProcessor) {
		DataContainer dc = new DataContainer(fieldProcessor);
		SelectionGroup filter = new SelectionGroup();
		dc.getSelectionGroups().add(filter);

		String aFilter = request.getParameter(APPLICATION_FILTER);
		String jFilter = request.getParameter(JOB_FILTER);
		String result = request.getParameter(RESULT_FILTER);
		String duration = request.getParameter(MIN_DURATION_FILTER);
		String start = request.getParameter(START_AFTER_FILTER);
		String end = request.getParameter(START_BEFORE_FILTER);

		addFilter(site, filter, request);

		List<JobRecord> records = jobRecordService.getRecords(site.getName(), aFilter, jFilter, start, end, result,
				duration);
		dc.setPage(records, fieldProcessor.getPageable());
		return dc;
	}

	private void addFilter(Site site, SelectionGroup filter, Request request) {
		List<String> appNames = jobRecordService.getDistinctElements(site.getName(), "application");
		appNames.add(0, "");
		Selection appFilter = new SelectionBuilder<String>(APPLICATION_FILTER)
				.title(MessageConstants.FILTER_RECORD_APPLICATION_NAME).select(request.getParameter(APPLICATION_FILTER))
				.options(appNames).build();
		filter.getSelections().add(appFilter);

		List<String> jobNames = jobRecordService.getDistinctElements(site.getName(), "job_name");
		jobNames.add(0, "");
		Selection jobFilter = new SelectionBuilder<String>(JOB_FILTER).title(MessageConstants.FILTER_RECORD_JOB_NAME)
				.select(request.getParameter(JOB_FILTER)).options(jobNames).build();
		filter.getSelections().add(jobFilter);

		Selection resultFilter = new SelectionBuilder<String>(RESULT_FILTER)
				.title(MessageConstants.FILTER_RECORD_RESULT).select(request.getParameter(RESULT_FILTER))
				.options(Lists.newArrayList("", ExecutionResult.SUCCESS.toString(), ExecutionResult.FAIL.toString()))
				.build();
		filter.getSelections().add(resultFilter);

		SelectionFactory selectionFactory = new SelectionFactory();

		filter.getSelections().add(selectionFactory.getTextSelection(MIN_DURATION_FILTER,
				MessageConstants.FILTER_RECORD_MIN_DURATION, request.getParameter(MIN_DURATION_FILTER)));

		Selection startedAfterFilter = selectionFactory.getDateSelection(START_AFTER_FILTER,
				MessageConstants.FILTER_RECORD_STARTED_AFTER, request.getParameter(START_AFTER_FILTER),
				START_FILTER_DATE_TIME_FORMAT);
		filter.getSelections().add(startedAfterFilter);

		Selection startedBeforeFilter = selectionFactory.getDateSelection(START_BEFORE_FILTER,
				MessageConstants.FILTER_RECORD_STARTED_BEFORE, request.getParameter(START_BEFORE_FILTER),
				START_FILTER_DATE_TIME_FORMAT);
		filter.getSelections().add(startedBeforeFilter);
	}

	public JobRecordService getJobRecordService() {
		return jobRecordService;
	}

	public void setJobRecordService(JobRecordService jobRecordService) {
		this.jobRecordService = jobRecordService;
	}

}
