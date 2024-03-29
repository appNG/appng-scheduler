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

import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import org.appng.api.ScheduledJobResult;
import org.appng.api.ScheduledJobResult.ExecutionResult;
import org.appng.api.model.Application;
import org.appng.api.model.Site;
import org.appng.api.support.CallableDataSource;
import org.appng.application.scheduler.SchedulingProperties;
import org.appng.application.scheduler.model.JobResult;
import org.appng.application.scheduler.quartz.RecordingJobListener;
import org.appng.application.scheduler.service.JobStateRestControllerTest;
import org.appng.core.domain.JobExecutionRecord;
import org.appng.core.repository.JobExecutionRecordRepository;
import org.appng.testsupport.TestBase;
import org.appng.testsupport.validation.WritingXmlValidator;
import org.appng.testsupport.validation.XPathDifferenceHandler;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobListener;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.JobDetailImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@ContextConfiguration(inheritLocations = false, locations = { TestBase.BEANS_PATH, TestBase.TESTCONTEXT_CORE,
		TestBase.TESTCONTEXT_JPA, "classpath:beans-test-core.xml" }, initializers = JobStateRestControllerTest.class)
public class TestJobRecordings extends TestBase {

	private @Mock JobExecutionContext jobContext;
	private @Autowired RecordingJobListener listener;
	private @Autowired JobExecutionRecordRepository recordRepo;
	private @Autowired SchedulingController schedulingController;

	static {
		WritingXmlValidator.writeXml = false;
	}

	public TestJobRecordings() {
		super("appng-scheduler", APPLICATION_HOME);
		setUseFullClassname(false);
		setEntityPackage(JobExecutionRecord.class.getPackage().getName());
		setRepositoryBase(JobExecutionRecordRepository.class.getPackage().getName());
	}

	@Override
	protected java.util.Properties getProperties() {
		return SchedulingProperties.getProperties();
	}

	@Test
	public void testJobDetails() throws Exception {
		Mockito.when(site.getApplications()).thenReturn(new HashSet<>(Arrays.asList(application)));
		schedulingController.start(site, application, environment);

		CallableDataSource callableDataSource = getDataSource("job").withParam("id", "appng-scheduler_longRunningJob")
				.getCallableDataSource();
		callableDataSource.perform("");
		validate(callableDataSource.getDatasource());
	}

	@Test
	public void testAddRecord() {
		ScheduledJobResult result = new ScheduledJobResult();
		result.setResult(ExecutionResult.SUCCESS);
		JobResult jobResult = createJobResult(listener, result, jobContext, site, application, "thejob");

		List<JobExecutionRecord> saved = recordRepo.findBySiteAndJobName(site.getName(), jobResult.getJobName());
		Assert.assertEquals(1, saved.size());
		Assert.assertEquals(jobResult.getSiteName(), saved.get(0).getSite());
		Assert.assertEquals(jobResult.getApplicationName(), saved.get(0).getApplication());
		Assert.assertEquals(jobResult.getJobName(), saved.get(0).getJobName());
		Assert.assertEquals(jobContext.getTrigger().getKey().toString(), saved.get(0).getTriggername());
		Assert.assertEquals(result.getResult().name(), saved.get(0).getResult());
	}

	public static JobResult createJobResult(JobListener listener, ScheduledJobResult result,
			JobExecutionContext jobContext, Site site, Application application, String jobName) {
		JobResult jobResult = new JobResult(result, application.getName(), site.getName(), jobName);
		when(jobContext.getResult()).thenReturn(jobResult);
		when(jobContext.getFireTime()).thenReturn(new Date());
		Trigger trigger = TriggerBuilder.newTrigger().withIdentity(site.getName(), application.getName())
				.forJob(jobResult.getJobName()).build();
		when(jobContext.getTrigger()).thenReturn(trigger);
		when(jobContext.getJobRunTime()).thenReturn(100l);
		JobDetailImpl value = new JobDetailImpl();
		value.setJobDataMap(new JobDataMap());
		when(jobContext.getJobDetail()).thenReturn(value);

		listener.jobWasExecuted(jobContext, null);
		return jobResult;
	}

	@Test
	public void testGetRecord() throws Exception {
		CallableDataSource datasource = getDataSource("record").withParam("recordId", "1").getCallableDataSource();
		datasource.perform("page");
		XPathDifferenceHandler diffHandler = new XPathDifferenceHandler(false);
		diffHandler.ignoreDifference("/datasource[1]/data[1]/result[1]/field[7]/value[1]/text()[1]");
		diffHandler.ignoreDifference("/datasource[1]/data[1]/result[1]/field[8]/value[1]/text()[1]");
		validate(datasource.getDatasource(), diffHandler);
	}

	@Test
	public void testGetRecordsForJobId() throws Exception {
		CallableDataSource datasource = getDataSource("records").withParam("jobId", "thejob").getCallableDataSource();
		datasource.perform("page");
		validate(datasource.getDatasource(), getResultsetDiffHandler());
	}

	@Test
	public void testGetRecordsWithFilter() throws Exception {
		request.addParameter(Records.APPLICATION_FILTER, "foo");
		request.addParameter(Records.JOB_FILTER, "bar");
		request.addParameter(Records.START_AFTER_FILTER, "2018-12-11");
		request.addParameter(Records.START_BEFORE_FILTER, "2018-12-12");
		request.addParameter(Records.MIN_DURATION_FILTER, "120");
		request.addParameter(Records.RESULT_FILTER, ExecutionResult.SUCCESS.name());
		CallableDataSource datasource = getDataSource("records").withParam("jobId", "thejob").getCallableDataSource();
		datasource.perform("page");
		validate(datasource.getDatasource(), getResultsetDiffHandler());
	}

	private XPathDifferenceHandler getResultsetDiffHandler() {
		XPathDifferenceHandler diffHandler = new XPathDifferenceHandler(false);
		diffHandler.ignoreDifference("/datasource[1]/data[1]/resultset[1]/result[1]/field[4]/value[1]/text()[1]");
		diffHandler.ignoreDifference("/datasource[1]/data[1]/resultset[1]/result[1]/field[5]/value[1]/text()[1]");
		return diffHandler;
	}

}
