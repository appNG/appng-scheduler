package org.appng.application.scheduler.business;

import java.util.Date;

import javax.sql.DataSource;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.appng.api.ScheduledJobResult.ExecutionResult;
import org.appng.application.scheduler.Constants;
import org.appng.application.scheduler.model.JobResult;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import org.springframework.jdbc.core.JdbcTemplate;

public class RecordingJobListener implements JobListener {

	private DataSource dataSource;

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	@Override
	public String getName() {
		return this.getClass().getName();
	}

	@Override
	public void jobToBeExecuted(JobExecutionContext context) {

	}

	@Override
	public void jobExecutionVetoed(JobExecutionContext context) {

	}

	@Override
	public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
		Object result = context.getResult();
		if (result instanceof JobResult) {
			JobResult jobResult = (JobResult) result;
			JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
			Object[] args = new Object[11];
			args[0] = jobResult.getApplicationName();
			args[1] = jobResult.getSiteName();
			args[2] = jobResult.getJobName();
			args[3] = context.getFireTime();
			args[4] = new Date();
			args[5] = context.getJobRunTime() / 1000;
			args[6] = context.getJobDetail().getJobDataMap().getBoolean(Constants.JOB_RUN_ONCE);
			// if the result is set, take it otherwise set it depending on the existence of an exception
			args[7] = null != jobResult.getResult() ? jobResult.getResult().toString()
					: null == jobException ? ExecutionResult.SUCCESS.toString() : ExecutionResult.FAIL.toString();
			args[8] = null == jobException ? null : ExceptionUtils.getStackTrace(jobException);
			args[9] = jobResult.getCustomData();
			args[10] = context.getTrigger().getKey().getName();
			jdbcTemplate.update(
					"INSERT INTO job_execution_record (application,site,job_name,start,end,duration,run_once,result,stacktraces,custom_data,triggername) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
					args);
		}
	}

}
