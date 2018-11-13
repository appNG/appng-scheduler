package org.appng.application.scheduler.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.appng.api.ScheduledJobResult;
import org.appng.api.ScheduledJobResult.ExecutionResult;
import org.appng.api.model.Application;
import org.appng.api.model.Site;
import org.appng.application.scheduler.Constants;
import org.appng.application.scheduler.PropertyConstants;
import org.appng.application.scheduler.model.JobRecord;
import org.appng.application.scheduler.model.JobResult;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class JobRecordService {

	private NamedParameterJdbcTemplate jdbcTemplate;

	public void recordJob(JobResult jobResult, Date fireTime, Date endTime, long jobRunTime, JobDataMap jobDataMap,
			JobExecutionException jobException, String triggerName) {

		// if the result is set, take it otherwise set it depending on the existence of an exception
		String result = null != jobResult.getResult() ? jobResult.getResult().toString()
				: null == jobException ? ExecutionResult.SUCCESS.toString() : ExecutionResult.FAIL.toString();

		MapSqlParameterSource paramsMap = new MapSqlParameterSource();
		paramsMap.addValue("application", jobResult.getApplicationName());
		paramsMap.addValue("site", jobResult.getSiteName());
		paramsMap.addValue("job_name", jobResult.getJobName());
		paramsMap.addValue("start", fireTime);
		paramsMap.addValue("end", endTime);
		paramsMap.addValue("duration", jobRunTime / 1000);
		paramsMap.addValue("run_once", jobDataMap.getBoolean(Constants.JOB_RUN_ONCE));
		paramsMap.addValue("result", result);
		paramsMap.addValue("stacktraces", null == jobException ? null : ExceptionUtils.getStackTrace(jobException));
		paramsMap.addValue("custom_data", jobResult.getCustomData());
		paramsMap.addValue("triggername", triggerName);

		jdbcTemplate.update(
				"INSERT INTO job_execution_record (application,site,job_name,start,end,duration,run_once,result,stacktraces,custom_data,triggername) VALUES (:application,:site,:job_name,:start,:end,:duration,:run_once,:result,:stacktraces,:custom_data,:triggername)",
				paramsMap);
	}

	public List<String> getDistinctElements(String siteName, String fieldName) {
		MapSqlParameterSource paramsMap = new MapSqlParameterSource();
		paramsMap.addValue("site", siteName);
		List<String> applications = jdbcTemplate.query("SELECT DISTINCT " + fieldName
				+ " FROM job_execution_record WHERE site = :site ORDER BY " + fieldName + " DESC", paramsMap,
				new RowMapper<String>() {
					@Override
					public String mapRow(ResultSet rs, int rowNum) throws SQLException {
						return rs.getString(fieldName);
					}
				});

		return applications;
	}

	public List<JobRecord> getRecords(String siteName, String applicationFilter, String jobFilter, String start,
			String end, String result, String duration) {
		MapSqlParameterSource paramsMap = new MapSqlParameterSource();

		StringBuilder sql = new StringBuilder(
				"SELECT site,application,job_name,duration,start,end,result,stacktraces FROM job_execution_record ");

		boolean first = true;

		first = addFiler("site", "=", siteName, sql, paramsMap, first);
		first = addFiler("application", "=", applicationFilter, sql, paramsMap, first);
		first = addFiler("job_name", "=", jobFilter, sql, paramsMap, first);
		first = addFiler("result", "=", result, sql, paramsMap, first);
		first = addFiler("start", ">", start, sql, paramsMap, first);
		first = addFiler("start", "<", end, sql, paramsMap, first, "end");
		first = addFiler("duration", ">=", duration, sql, paramsMap, first);

		sql.append(" ORDER BY start DESC;");

		List<JobRecord> records = jdbcTemplate.query(sql.toString(), paramsMap, new RowMapper<JobRecord>() {

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
				record.setStacktraces(rs.getString("stacktraces"));
				return record;
			}
		});

		return records;
	}

	private boolean addFiler(String filterName, String operator, String filterValue, StringBuilder sql,
			MapSqlParameterSource paramsMap, boolean first) {
		return addFiler(filterName, operator, filterValue, sql, paramsMap, first, filterName);
	}

	private boolean addFiler(String filterName, String operator, String filterValue, StringBuilder sql,
			MapSqlParameterSource paramsMap, boolean first, String paramName) {
		if (StringUtils.isNotBlank(filterValue)) {
			sql.append(first ? " WHERE " : " AND ");
			sql.append(filterName);
			sql.append(" ");
			sql.append(operator);
			sql.append(" :");
			sql.append(paramName);
			paramsMap.addValue(paramName, filterValue);
			return false;
		}
		return first;
	}

	public String cleanUp(Site site, Application application) {
		if (StringUtils.isNoneBlank(application.getProperties().getString(PropertyConstants.RECORD_LIFE_TIME))) {
			Integer lifetime = application.getProperties().getInteger(PropertyConstants.RECORD_LIFE_TIME);
			MapSqlParameterSource paramsMap = new MapSqlParameterSource();

			LocalDate outdated = LocalDate.now().minusDays(lifetime);
			paramsMap.addValue("outdated", outdated);
			paramsMap.addValue("site", site.getName());

			Long count = jdbcTemplate.queryForObject(
					"SELECT count(*) FROM job_execution_record WHERE site = :site AND start < :outdated ;", paramsMap,
					Long.class);
			if (count.longValue() > 0) {
				jdbcTemplate.update("DELETE FROM job_execution_record WHERE site = :site AND start < :outdated ;",
						paramsMap);
			}
			return count.toString();
		}
		return null;
	}

	public NamedParameterJdbcTemplate getJdbcTemplate() {
		return jdbcTemplate;
	}

	public void setJdbcTemplate(NamedParameterJdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

}
