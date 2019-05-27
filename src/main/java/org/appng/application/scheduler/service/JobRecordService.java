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
import org.springframework.stereotype.Component;

/**
 * Service class to deal with saving, deleting and querying for saved job execution records.
 * 
 * @author Claus Stümke
 *
 */
@Component
public class JobRecordService {

	private static final String FIELD_ID = "id";
	private static final String FIELD_TRIGGERNAME = "triggername";
	private static final String FIELD_CUSTOM_DATA = "custom_data";
	private static final String FIELD_STACKTRACES = "stacktraces";
	private static final String FIELD_RESULT = "result";
	private static final String FIELD_RUN_ONCE = "run_once";
	private static final String FIELD_DURATION = "duration";
	private static final String FIELD_END = "end_time";
	private static final String FIELD_START = "start_time";
	private static final String FIELD_JOB_NAME = "job_name";
	private static final String FIELD_SITE = "site";
	private static final String FIELD_APPLICATION = "application";
	private static final String PARAM_OUTDATED = "outdated";
	private static final String TABLE_NAME = "job_execution_record";

	private static final String QUERY_INSERT = String.format(
			"INSERT INTO %s (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s) VALUES (:%s,:%s,:%s,:%s,:%s,:%s,:%s,:%s,:%s,:%s,:%s)",
			TABLE_NAME, FIELD_APPLICATION, FIELD_SITE, FIELD_JOB_NAME, FIELD_START, FIELD_END, FIELD_DURATION,
			FIELD_RUN_ONCE, FIELD_RESULT, FIELD_STACKTRACES, FIELD_CUSTOM_DATA, FIELD_TRIGGERNAME, FIELD_APPLICATION,
			FIELD_SITE, FIELD_JOB_NAME, FIELD_START, FIELD_END, FIELD_DURATION, FIELD_RUN_ONCE, FIELD_RESULT,
			FIELD_STACKTRACES, FIELD_CUSTOM_DATA, FIELD_TRIGGERNAME);

	private static final String QUERY_SELECT = String.format("SELECT * FROM %s", TABLE_NAME);

	private static final String QUERY_SELECT_BY_ID_AND_SITE = String.format("%s WHERE %s = :%s AND %s= :%s",
			QUERY_SELECT, FIELD_SITE, FIELD_SITE, FIELD_ID, FIELD_ID);

	private static final String QUERY_DELETE_OUTDATED = String.format("DELETE FROM %s WHERE %s = :%s AND %s < :%s;",
			TABLE_NAME, FIELD_SITE, FIELD_SITE, FIELD_START, PARAM_OUTDATED);

	private static final String QUERY_COUNT_OUTDATED = String.format(
			"SELECT count(*) FROM %s WHERE %s = :%s AND %s < :%s ;", TABLE_NAME, FIELD_SITE, FIELD_SITE, FIELD_START,
			PARAM_OUTDATED);

	private NamedParameterJdbcTemplate jdbcTemplate;

	public JobRecordService(NamedParameterJdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void recordJob(JobResult jobResult, Date fireTime, Date endTime, long jobRunTime, JobDataMap jobDataMap,
			JobExecutionException jobException, String triggerName) {

		String result = getResult(jobResult, jobException);

		MapSqlParameterSource paramsMap = new MapSqlParameterSource();
		paramsMap.addValue(FIELD_APPLICATION, jobResult.getApplicationName());
		paramsMap.addValue(FIELD_SITE, jobResult.getSiteName());
		paramsMap.addValue(FIELD_JOB_NAME, jobResult.getJobName());
		paramsMap.addValue(FIELD_START, fireTime);
		paramsMap.addValue(FIELD_END, endTime);
		paramsMap.addValue(FIELD_DURATION, jobRunTime / 1000);
		paramsMap.addValue(FIELD_RUN_ONCE, jobDataMap.getBoolean(Constants.JOB_RUN_ONCE));
		paramsMap.addValue(FIELD_RESULT, result);
		paramsMap.addValue(FIELD_STACKTRACES, null == jobException ? null : ExceptionUtils.getStackTrace(jobException));
		paramsMap.addValue(FIELD_CUSTOM_DATA, jobResult.getCustomData());
		paramsMap.addValue(FIELD_TRIGGERNAME, triggerName);

		jdbcTemplate.update(QUERY_INSERT, paramsMap);
	}

	private String getResult(JobResult jobResult, JobExecutionException jobException) {
		// if the result is set, take it otherwise set it depending on the existence of an exception
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
		paramsMap.addValue(FIELD_SITE, siteName);
		String query = String.format("SELECT DISTINCT %s FROM %s WHERE %s = :%s ORDER BY %s DESC", fieldName,
				TABLE_NAME, FIELD_SITE, FIELD_SITE, fieldName);
		return jdbcTemplate.query(query, paramsMap, new RowMapper<String>() {
			public String mapRow(ResultSet rs, int rowNum) throws SQLException {
				return rs.getString(fieldName);
			}
		});
	}

	public List<JobRecord> getRecords(String siteName, String applicationFilter, String jobFilter, String start,
			String end, String result, String duration) {
		MapSqlParameterSource paramsMap = new MapSqlParameterSource();

		StringBuilder sql = new StringBuilder(QUERY_SELECT);

		boolean first = true;

		first = addFilter(FIELD_SITE, "=", siteName, sql, paramsMap, first);
		first = addFilter(FIELD_APPLICATION, "=", applicationFilter, sql, paramsMap, first);
		first = addFilter(FIELD_JOB_NAME, "=", jobFilter, sql, paramsMap, first);
		first = addFilter(FIELD_RESULT, "=", result, sql, paramsMap, first);
		first = addFilter(FIELD_START, ">", start, sql, paramsMap, first);
		first = addFiler(FIELD_START, "<", end, sql, paramsMap, first, FIELD_END);
		addFilter(FIELD_DURATION, ">=", duration, sql, paramsMap, first);

		sql.append(" ORDER BY " + FIELD_START + " DESC;");

		return jdbcTemplate.query(sql.toString(), paramsMap, new RecordRowMapper());

	}

	private boolean addFilter(String filterName, String operator, String filterValue, StringBuilder sql,
			MapSqlParameterSource paramsMap, boolean first) {
		return addFiler(filterName, operator, filterValue, sql, paramsMap, first, filterName);
	}

	private boolean addFiler(String filterName, String operator, String filterValue, StringBuilder sql,
			MapSqlParameterSource paramsMap, boolean first, String paramName) {
		if (StringUtils.isNotBlank(filterValue)) {
			sql.append(first ? " WHERE " : " AND ");
			sql.append(filterName);
			sql.append(StringUtils.SPACE);
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
			paramsMap.addValue(PARAM_OUTDATED, outdated);
			paramsMap.addValue(FIELD_SITE, site.getName());

			Long count = jdbcTemplate.queryForObject(QUERY_COUNT_OUTDATED, paramsMap, Long.class);
			if (count.longValue() > 0) {
				jdbcTemplate.update(QUERY_DELETE_OUTDATED, paramsMap);
			}
			return count.toString();
		}
		return null;
	}

	public JobRecord getRecord(String siteName, String recordId) {
		MapSqlParameterSource paramsMap = new MapSqlParameterSource();
		paramsMap.addValue(FIELD_SITE, siteName);
		paramsMap.addValue(FIELD_ID, recordId);

		JobRecord res = jdbcTemplate.queryForObject(QUERY_SELECT_BY_ID_AND_SITE, paramsMap, new RecordRowMapper());

		return res;
	}

	private class RecordRowMapper implements RowMapper<JobRecord> {
		public JobRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
			JobRecord record = new JobRecord();
			record.setId(rs.getInt(FIELD_ID));
			record.setSiteName(rs.getString(FIELD_SITE));
			record.setJobName(rs.getString(FIELD_JOB_NAME));
			record.setApplicationName(rs.getString(FIELD_APPLICATION));
			record.setStart(rs.getTimestamp(FIELD_START));
			record.setEnd(rs.getTimestamp(FIELD_END));
			record.setDuration(rs.getLong(FIELD_DURATION));
			record.setRunOnce(rs.getBoolean(FIELD_RUN_ONCE));
			record.setScheduledJobResult(new ScheduledJobResult());
			record.getScheduledJobResult().setResult(ExecutionResult.valueOf(rs.getString(FIELD_RESULT)));
			record.getScheduledJobResult().setCustomData(rs.getString(FIELD_CUSTOM_DATA));
			record.setStacktraces(rs.getString(FIELD_STACKTRACES));
			record.setTriggerName(rs.getString(FIELD_TRIGGERNAME));
			return record;
		}
	}

}
