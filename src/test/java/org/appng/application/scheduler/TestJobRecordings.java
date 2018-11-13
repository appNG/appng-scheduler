package org.appng.application.scheduler;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.Properties;

import org.appng.api.ScheduledJobResult;
import org.appng.application.scheduler.model.JobResult;
import org.appng.application.scheduler.quartz.RecordingJobListener;
import org.appng.testsupport.TestBase;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.Mock;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.TriggerBuilder;
import org.quartz.impl.JobDetailImpl;
import org.springframework.beans.factory.annotation.Autowired;
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

	@Override
	protected java.util.Properties getProperties() {
		Properties properties = new Properties();
		properties.put("indexExpression", "0 0/5 * * * ? 2042");
		properties.put("indexEnabled", "false");
		properties.put("site.name", "localhost");
		properties.put("validateJobsOnStartup", "false");
		properties.put("houseKeepingEnabled", "false");
		return properties;
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

}
