package org.appng.application.scheduler.service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;

import org.appng.api.ScheduledJobResult;
import org.appng.api.ScheduledJobResult.ExecutionResult;
import org.appng.application.scheduler.SchedulingProperties;
import org.appng.application.scheduler.business.SchedulingController;
import org.appng.application.scheduler.business.TestJobRecordings;
import org.appng.application.scheduler.quartz.RecordingJobListener;
import org.appng.core.domain.JobExecutionRecord;
import org.appng.core.repository.JobExecutionRecordRepository;
import org.appng.testsupport.TestBase;
import org.appng.testsupport.validation.WritingJsonValidator;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@ContextConfiguration(locations = { TestBase.TESTCONTEXT_CORE, TestBase.TESTCONTEXT_JPA,
		"classpath:beans-test-core.xml" }, initializers = JobStateRestControllerTest.class)
public class JobStateRestControllerTest extends TestBase {

	private @Mock JobExecutionContext jobContext;
	private @Autowired RecordingJobListener listener;
	private @Autowired SchedulingController schedulerController;
	private @Autowired MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter;

	public JobStateRestControllerTest() {
		super("appng-scheduler", APPLICATION_HOME);
		setEntityPackage(JobExecutionRecord.class.getPackage().getName());
		setRepositoryBase(JobExecutionRecordRepository.class.getPackage().getName());
	}

	@Before
	public void startController() {
		Mockito.when(site.getApplications()).thenReturn(new HashSet<>(Arrays.asList(application)));
		Mockito.when(site.getApplication("appng-scheduler")).thenReturn(application);
		schedulerController.start(site, application, environment);
		ScheduledJobResult result = new ScheduledJobResult();
		result.setResult(ExecutionResult.SUCCESS);
		TestJobRecordings.createJobResult(listener, result, jobContext, site, application,
				application.getName() + "_indexJob");
		TestJobRecordings.createJobResult(listener, result, jobContext, site, application,
				application.getName() + "_longRunningJob");
	}

	@Override
	protected Properties getProperties() {
		return SchedulingProperties.getProperties();
	}

	@Test
	public void testIndexJob() throws Exception {
		runTest("indexJob");
	}

	@Test
	public void testLongRunningJob() throws Exception {
		runTest("longRunningJob");
	}

	protected void runTest(String jobName) throws Exception {
		MockMvc mvc = MockMvcBuilders.standaloneSetup(context.getBean(JobStateRestController.class))
				.setCustomArgumentResolvers(getHandlerMethodArgumentResolver())
				.setMessageConverters(mappingJackson2HttpMessageConverter).build();

		ResultActions response = mvc.perform(
				get("/jobState/appng-scheduler/" + jobName).header(HttpHeaders.AUTHORIZATION, "Bearer TheBearer")
						.contentType(MediaType.APPLICATION_JSON).characterEncoding("utf-8"));
		MvcResult result = response.andExpect(status().is(HttpStatus.OK.value())).andReturn();
		String controlFile = "json/RecordsRestControllerTest-" + jobName + ".json";
		String json = result.getResponse().getContentAsString().replaceFirst("\\d{4}.*:\\d{2}",
				"2021-09-23T09:01:15.476+02:00");
		WritingJsonValidator.validate(json, controlFile);
	}
}
