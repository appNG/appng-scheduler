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
package org.appng.application.scheduler;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.appng.api.FieldProcessor;
import org.appng.api.ProcessingException;
import org.appng.api.model.Application;
import org.appng.api.support.CallableAction;
import org.appng.api.support.CallableDataSource;
import org.appng.application.scheduler.business.SchedulingController;
import org.appng.application.scheduler.model.JobForm;
import org.appng.application.scheduler.model.JobModel;
import org.appng.application.scheduler.service.JobStateRestControllerTest;
import org.appng.core.domain.JobExecutionRecord;
import org.appng.core.repository.JobExecutionRecordRepository;
import org.appng.testsupport.TestBase;
import org.appng.testsupport.validation.WritingXmlValidator;
import org.appng.testsupport.validation.XPathDifferenceHandler;
import org.appng.xml.platform.Data;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@ContextConfiguration(inheritLocations = false, locations = { TestBase.BEANS_PATH, TestBase.TESTCONTEXT_CORE,
		TestBase.TESTCONTEXT_JPA, "classpath:beans-test.xml" }, initializers = JobStateRestControllerTest.class)
public class SchedulingTest extends TestBase {

	static {
		WritingXmlValidator.writeXml = false;
	}

	@Autowired
	SchedulingController controller;

	public SchedulingTest() {
		super("appng-scheduler", APPLICATION_HOME);
		setEntityPackage(JobExecutionRecord.class.getPackage().getName());
		setRepositoryBase(JobExecutionRecordRepository.class.getPackage().getName());
	}

	@Override
	protected java.util.Properties getProperties() {
		return SchedulingProperties.getProperties();
	}

	@Test
	public void testCreate() throws ProcessingException, IOException {
		JobForm form = new JobForm();
		JobModel jobModel = new JobModel();
		form.setJobModel(jobModel);
		jobModel.setAvailableJob("appng-scheduler_indexJob");
		jobModel.setName("mytestjob");
		jobModel.setCronExpression("0 0/10 * * * ? 2042");
		jobModel.setDescription("description");
		ActionCall action = getAction("jobEvent", "create").withParam("form_action", "create");
		FieldProcessor fp = action.getCallableAction(form).perform();
		validate(fp.getMessages(), "-job1");

		jobModel.setName("anotherJob");
		jobModel.setCronExpression("0 0/20 * * * ? 2042");
		jobModel.setDescription("another description");

		fp = action.getCallableAction(form).perform();
		validate(fp.getMessages(), "-job2");
	}

	@Test
	public void testDelete() throws ProcessingException, IOException {
		CallableAction callableAction = getAction("jobEvent", "delete").withParam("form_action", "delete")
				.withParam("id", "appng-scheduler_mytestjob").getCallableAction(null);
		validate(callableAction.perform().getMessages());
	}

	@Test
	public void testSchedule() throws ProcessingException, IOException {
		CallableAction callableAction = getAction("jobEvent", "schedule").withParam("form_action", "schedule")
				.withParam("id", "appng-scheduler_indexJob").getCallableAction(null);
		validate(callableAction.perform().getMessages());
	}

	@Test
	public void testStart() throws ProcessingException, IOException, InterruptedException {
		CallableAction callableAction = getAction("jobEvent", "start").withParam("form_action", "start")
				.withParam("id", "appng-scheduler_indexJob").getCallableAction(null);
		validate(callableAction.perform().getMessages());
	}

	@Test
	public void testUnschedule() throws ProcessingException, IOException {
		CallableAction callableAction = getAction("jobEvent", "unschedule").withParam("form_action", "unschedule")
				.withParam("id", "appng-scheduler_indexJob").getCallableAction(null);
		validate(callableAction.perform().getMessages());
	}

	@Test
	public void testUpdate() throws ProcessingException, IOException {
		JobModel jobModel = new JobModel();
		jobModel.setCronExpression("0 0/30 * * * ? 2042");
		jobModel.setDescription("description");
		CallableAction callableAction = getAction("jobEvent", "update").withParam("form_action", "update")
				.withParam("id", "appng-scheduler_anotherJob").getCallableAction(jobModel);
		validate(callableAction.perform().getMessages());
	}

	@Test
	public void testShowJob() throws ProcessingException, IOException {
		DataSourceCall dataSource = getDataSource("job").withParam("id", "appng-scheduler_indexJob");
		CallableDataSource singleJob = dataSource.getCallableDataSource();
		singleJob.perform("");
		validate(singleJob.getDatasource());
	}

	@Test
	public void testShowJobs() throws ProcessingException, IOException {
		DataSourceCall dataSource = getDataSource("jobs");
		Data data = dataSource.getCallableDataSource().perform("");
		XPathDifferenceHandler diffHandler = new XPathDifferenceHandler(false);
		diffHandler.ignoreDifference("/data[1]/resultset[1]/result[4]/field[5]/value[1]/text()[1]");
		validate(data, diffHandler);
	}

	@Before
	public void startController() {
		Set<Application> hashSet = new HashSet<Application>();
		hashSet.add(application);
		Mockito.when(site.getApplications()).thenReturn(hashSet);
		Mockito.when(site.getApplication("appng-scheduler")).thenReturn(application);
		controller.start(site, application, environment);
	}

}
