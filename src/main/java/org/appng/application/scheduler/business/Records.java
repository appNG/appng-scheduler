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

import com.google.common.collect.Lists;

public class Records implements DataProvider {

	private static final String START_FILTER_DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm";
	private static final String APPLICATION_FILTER = "ap";
	private static final String JOB_FILTER = "job";
	private static final String START_AFTER_FILTER = "sa";
	private static final String START_BEFORE_FILTER = "sb";
	private static final String MIN_DURATION_FILTER = "du";
	private static final String RESULT_FILTER = "re";

	private JobRecordService jobRecordService;

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

		addFilter(site, filter, aFilter, jFilter, result, duration, start, end);

		List<JobRecord> records = jobRecordService.getRecords(site.getName(), aFilter, jFilter, start, end, result,
				duration);
		dc.setPage(records, fieldProcessor.getPageable());
		return dc;
	}

	private void addFilter(Site site, SelectionGroup filter, String aFilter, String jFilter, String result,
			String duration, String start, String end) {
		List<String> appNames = jobRecordService.getDistinctElements(site.getName(), "application");
		appNames.add(0, "");
		Selection appFilter = new SelectionBuilder<String>(APPLICATION_FILTER).title(APPLICATION_FILTER).select(aFilter)
				.options(appNames).build();
		filter.getSelections().add(appFilter);

		List<String> jobNames = jobRecordService.getDistinctElements(site.getName(), "job_name");
		jobNames.add(0, "");
		Selection jobFilter = new SelectionBuilder<String>(JOB_FILTER).title(JOB_FILTER).select(jFilter)
				.options(jobNames).build();
		filter.getSelections().add(jobFilter);

		Selection resultFilter = new SelectionBuilder<String>(RESULT_FILTER)
				.title(MessageConstants.FILTER_RECORD_RESULT).select(result)
				.options(Lists.newArrayList("", ExecutionResult.SUCCESS.toString(), ExecutionResult.FAIL.toString()))
				.build();
		filter.getSelections().add(resultFilter);

		SelectionFactory selectionFactory = new SelectionFactory();

		filter.getSelections().add(selectionFactory.getTextSelection(MIN_DURATION_FILTER,
				MessageConstants.FILTER_RECORD_MIN_DURATION, duration));

		Selection startedAfterFilter = selectionFactory.getDateSelection(START_AFTER_FILTER,
				MessageConstants.FILTER_RECORD_STARTED_AFTER, start, START_FILTER_DATE_TIME_FORMAT);
		filter.getSelections().add(startedAfterFilter);

		Selection startedBeforeFilter = selectionFactory.getDateSelection(START_BEFORE_FILTER,
				MessageConstants.FILTER_RECORD_STARTED_BEFORE, end, START_FILTER_DATE_TIME_FORMAT);
		filter.getSelections().add(startedBeforeFilter);
	}

	public JobRecordService getJobRecordService() {
		return jobRecordService;
	}

	public void setJobRecordService(JobRecordService jobRecordService) {
		this.jobRecordService = jobRecordService;
	}

}
