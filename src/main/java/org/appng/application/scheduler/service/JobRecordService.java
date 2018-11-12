package org.appng.application.scheduler.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;

import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.appng.api.ScheduledJobResult;
import org.appng.api.ScheduledJobResult.ExecutionResult;
import org.appng.api.model.Site;
import org.appng.application.scheduler.Constants;
import org.appng.application.scheduler.model.JobRecord;
import org.appng.application.scheduler.model.JobResult;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class JobRecordService {

	private DataSource dataSource;

	public void recordJob(JobResult jobResult, Date fireTime, Date endTime, long jobRunTime, JobDataMap jobDataMap,
			JobExecutionException jobException, String triggerName) {

		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		Object[] args = new Object[11];
		args[0] = jobResult.getApplicationName();
		args[1] = jobResult.getSiteName();
		args[2] = jobResult.getJobName();
		args[3] = fireTime;
		args[4] = endTime;
		args[5] = jobRunTime / 1000;
		args[6] = jobDataMap.getBoolean(Constants.JOB_RUN_ONCE);
		// if the result is set, take it otherwise set it depending on the existence of an exception
		args[7] = null != jobResult.getResult() ? jobResult.getResult().toString()
				: null == jobException ? ExecutionResult.SUCCESS.toString() : ExecutionResult.FAIL.toString();
		args[8] = null == jobException ? null : ExceptionUtils.getStackTrace(jobException);
		args[9] = jobResult.getCustomData();
		args[10] = triggerName;
		jdbcTemplate.update(
				"INSERT INTO job_execution_record (application,site,job_name,start,end,duration,run_once,result,stacktraces,custom_data,triggername) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
				args);
	}

	// public List<String> getApplications(String name) {
	// Object[] args = { name };
	// List<String> applications = new JdbcTemplate(dataSource).query(
	// "SELECT DISTINCT application FROM job_execution_record WHERE site = ? ORDER BY application DESC", args,
	// new RowMapper<String>() {
	//
	// @Override
	// public String mapRow(ResultSet rs, int rowNum) throws SQLException {
	// return rs.getString("application");
	// }
	// });
	//
	// return applications;
	// }

	public List<String> getDistinctElements(String siteName, String fieldName) {
		Object[] args = { siteName };
		List<String> applications = new JdbcTemplate(dataSource).query("SELECT DISTINCT " + fieldName
				+ " FROM job_execution_record WHERE site = ? ORDER BY " + fieldName + " DESC", args,
				new RowMapper<String>() {

					@Override
					public String mapRow(ResultSet rs, int rowNum) throws SQLException {
						return rs.getString(fieldName);
					}
				});

		return applications;
	}

	public List<JobRecord> getRecords(Site site, String applicationFilter, String jobFilter, String start, String end,
			String result, String duration) {
		NamedParameterJdbcTemplate template = new NamedParameterJdbcTemplate(dataSource);
		MapSqlParameterSource paramsMap = new MapSqlParameterSource();

		StringBuilder sql = new StringBuilder(
				"SELECT site,application,job_name,duration,start,end,result FROM job_execution_record WHERE site = :site ");
		paramsMap.addValue("site", site.getName());

		if (StringUtils.isNotBlank(applicationFilter)) {
			sql.append(" AND application = :application ");
			paramsMap.addValue("application", applicationFilter);
		}

		if (StringUtils.isNotBlank(jobFilter)) {
			sql.append(" AND job_name = :job ");
			paramsMap.addValue("job", jobFilter);
		}

		if (StringUtils.isNotBlank(result)) {
			sql.append(" AND result = :result ");
			paramsMap.addValue("result", result);
		}

		if (StringUtils.isNotBlank(start)) {
			sql.append(" AND start > :start ");
			paramsMap.addValue("start", start);
		}

		if (StringUtils.isNotBlank(end)) {
			sql.append(" AND start < :end ");
			paramsMap.addValue("end", end);
		}

		if (StringUtils.isNotBlank(duration)) {
			sql.append(" AND duration > :duration ");
			paramsMap.addValue("duration", duration);
		}

		sql.append("ORDER BY start DESC;");

		List<JobRecord> records = template.query(sql.toString(), paramsMap, new RowMapper<JobRecord>() {

			@Override
			public JobRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
				JobRecord record = new JobRecord();
				record.setSiteName(rs.getString("site"));
				record.setJobName(rs.getString("job_name"));
				record.setApplicationName(rs.getString("application"));
				record.setStart(rs.getTimestamp("start"));
				record.setEnd(rs.getTimestamp("end"));
				record.setDuration(rs.getLong("duration"));
				record.setScheduledJobResult(new ScheduledJobResult());
				record.getScheduledJobResult().setResult(ExecutionResult.valueOf(rs.getString("result")));
				return record;
			}
		});

		return records;
	}

	public DataSource getDataSource() {
		return dataSource;
	}

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}

}
