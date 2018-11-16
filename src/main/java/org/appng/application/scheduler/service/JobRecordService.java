/*
 * Copyright 2011-2017 the original author or authors.
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

/**
 * Service class to deal with saving, deleting and querying for saved job execution records.
 * 
 * @author Claus Stümke
 *
 */
public class JobRecordService {

	private static final String QUERY_INSERT = "INSERT INTO job_execution_record (application,site,job_name,start,end,duration,run_once,result,stacktraces,custom_data,triggername) VALUES (:application,:site,:job_name,:start,:end,:duration,:run_once,:result,:stacktraces,:custom_data,:triggername)";
	private static final String QUERY_SELECT_RECORD = "SELECT site,application,job_name,duration,start,end,result,stacktraces FROM job_execution_record ";
	private static final String QUERY_DELETE_OUTDATED = "DELETE FROM job_execution_record WHERE site = :site AND start < :outdated ;";
	private static final String QUERY_COUNT_OUTDATED = "SELECT count(*) FROM job_execution_record WHERE site = :site AND start < :outdated ;";

	private static final String FIELD_NAME_TRIGGERNAME = "triggername";
	private static final String FIELD_NAME_CUSTOM_DATA = "custom_data";
	private static final String FIELD_NAME_STACKTRACES = "stacktraces";
	private static final String FIELD_NAME_RESULT = "result";
	private static final String FIELD_NAME_RUN_ONCE = "run_once";
	private static final String FIELD_NAME_DURATION = "duration";
	private static final String FIELD_NAME_END = "end";
	private static final String FIELD_NAME_START = "start";
	private static final String FIELD_NAME_JOB_NAME = "job_name";
	private static final String FIELD_NAME_SITE = "site";
	private static final String FIELD_NAME_APPLICATION = "application";

	private NamedParameterJdbcTemplate jdbcTemplate;

	public void recordJob(JobResult jobResult, Date fireTime, Date endTime, long jobRunTime, JobDataMap jobDataMap,
			JobExecutionException jobException, String triggerName) {

		// if the result is set, take it otherwise set it depending on the existence of an exception
		String result = null;
		result = getResult(jobResult, jobException);

		MapSqlParameterSource paramsMap = new MapSqlParameterSource();
		paramsMap.addValue(FIELD_NAME_APPLICATION, jobResult.getApplicationName());
		paramsMap.addValue(FIELD_NAME_SITE, jobResult.getSiteName());
		paramsMap.addValue(FIELD_NAME_JOB_NAME, jobResult.getJobName());
		paramsMap.addValue(FIELD_NAME_START, fireTime);
		paramsMap.addValue(FIELD_NAME_END, endTime);
		paramsMap.addValue(FIELD_NAME_DURATION, jobRunTime / 1000);
		paramsMap.addValue(FIELD_NAME_RUN_ONCE, jobDataMap.getBoolean(Constants.JOB_RUN_ONCE));
		paramsMap.addValue(FIELD_NAME_RESULT, result);
		paramsMap.addValue(FIELD_NAME_STACKTRACES,
				null == jobException ? null : ExceptionUtils.getStackTrace(jobException));
		paramsMap.addValue(FIELD_NAME_CUSTOM_DATA, jobResult.getCustomData());
		paramsMap.addValue(FIELD_NAME_TRIGGERNAME, triggerName);

		jdbcTemplate.update(QUERY_INSERT, paramsMap);
	}

	private String getResult(JobResult jobResult, JobExecutionException jobException) {
		if (null != jobResult.getResult()) {
			return jobResult.getResult().toString();
		} else {
			if (null == jobException) {
				return ExecutionResult.SUCCESS.toString();
			}
			return ExecutionResult.FAIL.toString();
		}
	}

	public List<String> getDistinctElements(String siteName, String fieldName) {
		MapSqlParameterSource paramsMap = new MapSqlParameterSource();
		paramsMap.addValue(FIELD_NAME_SITE, siteName);
		return jdbcTemplate.query("SELECT DISTINCT " + fieldName
				+ " FROM job_execution_record WHERE site = :site ORDER BY " + fieldName + " DESC", paramsMap,
				new RowMapper<String>() {
					@Override
					public String mapRow(ResultSet rs, int rowNum) throws SQLException {
						return rs.getString(fieldName);
					}
				});
	}

	public List<JobRecord> getRecords(String siteName, String applicationFilter, String jobFilter, String start,
			String end, String result, String duration) {
		MapSqlParameterSource paramsMap = new MapSqlParameterSource();

		StringBuilder sql = new StringBuilder(QUERY_SELECT_RECORD);

		boolean first = true;

		first = addFiler(FIELD_NAME_SITE, "=", siteName, sql, paramsMap, first);
		first = addFiler(FIELD_NAME_APPLICATION, "=", applicationFilter, sql, paramsMap, first);
		first = addFiler(FIELD_NAME_JOB_NAME, "=", jobFilter, sql, paramsMap, first);
		first = addFiler(FIELD_NAME_RESULT, "=", result, sql, paramsMap, first);
		first = addFiler(FIELD_NAME_START, ">", start, sql, paramsMap, first);
		first = addFiler(FIELD_NAME_START, "<", end, sql, paramsMap, first, FIELD_NAME_END);
		addFiler(FIELD_NAME_DURATION, ">=", duration, sql, paramsMap, first);

		sql.append(" ORDER BY start DESC;");

		return jdbcTemplate.query(sql.toString(), paramsMap, new RowMapper<JobRecord>() {

			@Override
			public JobRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
				JobRecord record = new JobRecord();
				record.setSiteName(rs.getString(FIELD_NAME_SITE));
				record.setJobName(rs.getString(FIELD_NAME_JOB_NAME));
				record.setApplicationName(rs.getString(FIELD_NAME_APPLICATION));
				record.setStart(rs.getTimestamp(FIELD_NAME_START));
				record.setEnd(rs.getTimestamp(FIELD_NAME_END));
				record.setDuration(rs.getLong(FIELD_NAME_DURATION));
				record.setScheduledJobResult(new ScheduledJobResult());
				record.getScheduledJobResult().setResult(ExecutionResult.valueOf(rs.getString(FIELD_NAME_RESULT)));
				record.setStacktraces(rs.getString(FIELD_NAME_STACKTRACES));
				return record;
			}
		});

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
			paramsMap.addValue(FIELD_NAME_SITE, site.getName());

			Long count = jdbcTemplate.queryForObject(QUERY_COUNT_OUTDATED, paramsMap, Long.class);
			if (count.longValue() > 0) {
				jdbcTemplate.update(QUERY_DELETE_OUTDATED, paramsMap);
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
