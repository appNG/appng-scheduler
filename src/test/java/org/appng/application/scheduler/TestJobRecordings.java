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
package org.appng.application.scheduler;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.Properties;

import org.appng.api.ScheduledJobResult;
import org.appng.api.support.ApplicationRequest;
import org.appng.api.support.CallableDataSource;
import org.appng.application.scheduler.model.JobResult;
import org.appng.application.scheduler.quartz.RecordingJobListener;
import org.appng.testsupport.TestBase;
import org.appng.testsupport.validation.WritingXmlValidator;
import org.appng.xml.platform.Data;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.TriggerBuilder;
import org.quartz.impl.JobDetailImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@ContextConfiguration(locations = {
		"classpath:beans.xml" }, inheritLocations = true, initializers = TestJobRecordings.class)
public class TestJobRecordings extends TestBase {

	@Autowired
	NamedParameterJdbcTemplate jdbcTemplate;

	@Mock
	JobExecutionContext jobContext;

	static {
		WritingXmlValidator.writeXml = false;
	}

	@Override
	protected java.util.Properties getProperties() {
		Properties properties = new Properties();
		properties.put("indexExpression", "0 0/5 * * * ? 2042");
		properties.put("houseKeepingExpression", "0 0/5 * * * ? 2042");
		properties.put("indexEnabled", "false");
		properties.put("site.name", "localhost");
		properties.put("validateJobsOnStartup", "false");
		properties.put("houseKeepingEnabled", "false");
		return properties;
	}

	@Before
	public void initTest() {
		Mockito.reset(jdbcTemplate);

	}

	@Test
	public void testAddRecord() {
		when(jobContext.getResult()).thenReturn(new JobResult(new ScheduledJobResult(), "test", "test", "name"));
		when(jobContext.getFireTime()).thenReturn(new Date());
		when(jobContext.getTrigger())
				.thenReturn(TriggerBuilder.newTrigger().withIdentity("test", "test").forJob("test").build());
		when(jobContext.getJobRunTime()).thenReturn(100l);
		JobDetailImpl value = new JobDetailImpl();
		value.setJobDataMap(new JobDataMap());
		when(jobContext.getJobDetail()).thenReturn(value);
		RecordingJobListener listener = context.getBean(RecordingJobListener.class);
		listener.jobWasExecuted(jobContext, null);
		verify(jdbcTemplate, times(1)).update(anyString(), any(MapSqlParameterSource.class));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testGetRecords() throws Exception {

		CallableDataSource datasource = getDataSource("records").getCallableDataSource();
		datasource.perform("page");
		validate(datasource.getDatasource());
		verify(jdbcTemplate).query(eq(
				"SELECT DISTINCT application FROM job_execution_record WHERE site = :site ORDER BY application DESC"),
				any(MapSqlParameterSource.class), any(RowMapper.class));
		verify(jdbcTemplate).query(
				eq("SELECT DISTINCT job_name FROM job_execution_record WHERE site = :site ORDER BY job_name DESC"),
				any(MapSqlParameterSource.class), any(RowMapper.class));
		verify(jdbcTemplate).query(
				eq("SELECT * FROM job_execution_record WHERE site = :site ORDER BY start_time DESC;"),
				any(MapSqlParameterSource.class), any(RowMapper.class));

	}

	@SuppressWarnings("unchecked")
	@Test
	public void testGetRecordsForJobId() throws Exception {

		CallableDataSource datasource = getDataSource("records").withParam("jobId", "foo").getCallableDataSource();
		datasource.perform("page");
		validate(datasource.getDatasource());
		verify(jdbcTemplate, never()).query(eq(
				"SELECT DISTINCT application FROM job_execution_record WHERE site = :site ORDER BY application DESC"),
				any(MapSqlParameterSource.class), any(RowMapper.class));
		verify(jdbcTemplate, never()).query(
				eq("SELECT DISTINCT job_name FROM job_execution_record WHERE site = :site ORDER BY job_name DESC"),
				any(MapSqlParameterSource.class), any(RowMapper.class));
		verify(jdbcTemplate, times(1)).query(eq(
				"SELECT * FROM job_execution_record WHERE site = :site AND job_name = :job_name ORDER BY start_time DESC;"),
				any(MapSqlParameterSource.class), any(RowMapper.class));

	}

	@SuppressWarnings("unchecked")
	@Test
	public void testGetRecordsWithFilter() throws Exception {

		((ApplicationRequest) request).addParameter("ap", "foo");
		((ApplicationRequest) request).addParameter("job", "bar");
		((ApplicationRequest) request).addParameter("sa", "2018-12-11");
		((ApplicationRequest) request).addParameter("sb", "2018-12-12");
		((ApplicationRequest) request).addParameter("du", "120");
		((ApplicationRequest) request).addParameter("re", "SUCCESS");

		CallableDataSource datasource = getDataSource("records").getCallableDataSource();
		Data perform = datasource.perform("page");
		validate(datasource.getDatasource());
		verify(jdbcTemplate).query(eq(
				"SELECT DISTINCT application FROM job_execution_record WHERE site = :site ORDER BY application DESC"),
				any(MapSqlParameterSource.class), any(RowMapper.class));
		verify(jdbcTemplate).query(
				eq("SELECT DISTINCT job_name FROM job_execution_record WHERE site = :site ORDER BY job_name DESC"),
				any(MapSqlParameterSource.class), any(RowMapper.class));
		verify(jdbcTemplate).query(eq(
				"SELECT * FROM job_execution_record WHERE site = :site AND application = :application AND job_name = :job_name AND result = :result AND start_time > :start_time AND start_time < :end_time AND duration >= :duration ORDER BY start_time DESC;"),
				any(MapSqlParameterSource.class), any(RowMapper.class));
	}

}
