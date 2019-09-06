/*
 * Copyright 2011-2019 the original author or authors.
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

import java.text.ParseException;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;
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
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;

/**
 * A {@link DataProvider} for recorded job executions. It supports some filters.
 * 
 * @author Claus Stümke
 */

@Component
public class Records implements DataProvider {

	private static final String START_FILTER_DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm";
	protected static final String APPLICATION_FILTER = "ap";
	protected static final String JOB_FILTER = "job";
	protected static final String START_AFTER_FILTER = "sa";
	protected static final String START_BEFORE_FILTER = "sb";
	protected static final String MIN_DURATION_FILTER = "du";
	protected static final String RESULT_FILTER = "re";

	private JobRecordService jobRecordService;

	public Records(JobRecordService jobRecordService) {
		this.jobRecordService = jobRecordService;
	}

	public DataContainer getData(Site site, Application application, Environment environment, Options options,
			Request request, FieldProcessor fp) {
		DataContainer dc = new DataContainer(fp);
		Integer recordId = options.getInteger("id", "value");
		String jobId = options.getOptionValue("jobId", "value");
		if (StringUtils.isNotBlank(jobId)) {
			Page<JobRecord> records = jobRecordService.getRecords(site.getName(), null, jobId, null, null, null, null,
					fp.getPageable());
			dc.setPage(records);
		} else if (null != recordId) {
			JobRecord item = jobRecordService.getRecord(recordId);
			dc.setItem(item);
		} else {
			SelectionGroup filter = new SelectionGroup();
			dc.getSelectionGroups().add(filter);

			String aFilter = request.getParameter(APPLICATION_FILTER);
			String jFilter = request.getParameter(JOB_FILTER);
			String result = request.getParameter(RESULT_FILTER);
			Integer duration = request.convert(request.getParameter(MIN_DURATION_FILTER), Integer.class);
			String start = request.getParameter(START_AFTER_FILTER);
			String end = request.getParameter(START_BEFORE_FILTER);

			addFilter(site, filter, request);

			Date startDate = getDate(start);
			Date endDate = getDate(end);

			Page<JobRecord> records = jobRecordService.getRecords(site.getName(), aFilter, jFilter, startDate, endDate,
					result, duration, fp.getPageable());
			dc.setPage(records);
		}
		return dc;
	}

	public static Date getDate(String value) {
		if (StringUtils.isNotBlank(value)) {
			try {
				return FastDateFormat.getInstance(START_FILTER_DATE_TIME_FORMAT).parse(value);
			} catch (ParseException e) {
			}
		}
		return null;
	}

	private void addFilter(Site site, SelectionGroup filter, Request request) {
		List<String> appNames = jobRecordService.getDistinctElements(site.getName(), "application");
		appNames.add(0, StringUtils.EMPTY);
		Selection appFilter = new SelectionBuilder<String>(APPLICATION_FILTER)
				.title(MessageConstants.FILTER_RECORD_APPLICATION_NAME).select(request.getParameter(APPLICATION_FILTER))
				.options(appNames).build();
		filter.getSelections().add(appFilter);

		List<String> jobNames = jobRecordService.getDistinctElements(site.getName(), "job_name");
		jobNames.add(0, StringUtils.EMPTY);
		Selection jobFilter = new SelectionBuilder<String>(JOB_FILTER).title(MessageConstants.FILTER_RECORD_JOB_NAME)
				.select(request.getParameter(JOB_FILTER)).options(jobNames).build();
		filter.getSelections().add(jobFilter);

		Selection resultFilter = new SelectionBuilder<String>(RESULT_FILTER)
				.title(MessageConstants.FILTER_RECORD_RESULT).select(request.getParameter(RESULT_FILTER))
				.options(Lists.newArrayList(StringUtils.EMPTY, ExecutionResult.SUCCESS.toString(),
						ExecutionResult.FAIL.toString()))
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

}
