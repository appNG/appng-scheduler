/*
 * Copyright 2011-2020 the original author or authors.
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

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.appng.api.ScheduledJobResult.ExecutionResult;
import org.appng.api.model.Site;
import org.appng.application.scheduler.Constants;
import org.appng.core.domain.JobExecutionRecord;
import org.appng.scheduler.openapi.model.JobRecord;
import org.appng.scheduler.openapi.model.JobState;
import org.appng.scheduler.openapi.model.JobState.TimeunitEnum;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

/**
 * {@link RestController} providing a REST API to query for {@link JobState}s
 * 
 * @author Matthias Müller
 */
@Slf4j
@RestController
public class JobStateRestController {

	private JobRecordService jobRecordService;
	private Scheduler scheduler;
	@Value("${bearerToken}")
	private String bearerToken;

	public JobStateRestController(JobRecordService jobRecordService, Scheduler scheduler) {
		this.jobRecordService = jobRecordService;
		this.scheduler = scheduler;
	}

	@RequestMapping(value = "/jobState/{application}/{job}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
	public ResponseEntity<JobState> getJobState(
			@PathVariable(required = true, name = "application") String applicationName,
			@PathVariable(required = true, name = "job") String jobName,
			@RequestParam(required = false, name = "pageSize", defaultValue = "10") Integer pageSize,
			@RequestParam(required = false, name = "records") boolean withRecords,
			@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) List<String> auths, Site site) {
		if (!isValidBearer(auths)) {
			return new ResponseEntity<>(null, HttpStatus.UNAUTHORIZED);
		}

		JobState jobState = getJobState(applicationName, jobName, site, pageSize, withRecords);
		if (null != jobState) {
			return ResponseEntity.ok(jobState);
		}
		return ResponseEntity.notFound().build();
	}

	private JobState getJobState(String application, String job, Site site, Integer pageSize, boolean withRecords) {
		JobState jobState = null;
		try {
			String jobName = job.startsWith(application) ? job : application + "_" + job;
			JobKey jobKey = new JobKey(jobName, site.getName());
			JobDetail jobDetail = scheduler.getJobDetail(jobKey);
			if (null != jobDetail) {

				jobState = new JobState();
				jobState.setSite(site.getName());
				jobState.setApplication(application);
				jobState.setJob(jobName);

				JobDataMap jobDataMap = jobDetail.getJobDataMap();
				boolean hasWarnTreshold = jobDataMap.containsKey(Constants.THRESHOLD_WARN);
				if (hasWarnTreshold) {
					jobState.setThresholdWarn(jobDataMap.getInt(Constants.THRESHOLD_WARN));
				}
				boolean hasErrorTreshold = jobDataMap.containsKey(Constants.THRESHOLD_ERROR);
				if (hasErrorTreshold) {
					jobState.setThresholdError(jobDataMap.getInt(Constants.THRESHOLD_ERROR));
				}
				if (jobDataMap.containsKey(Constants.THRESHOLD_TIMEUNIT)) {
					jobState.setTimeunit(
							TimeunitEnum.valueOf(jobDataMap.getString(Constants.THRESHOLD_TIMEUNIT).toUpperCase()));
				}

				Date now = new Date();
				TimeunitEnum timeunit = jobState.getTimeunit();
				boolean hasTimeUnit = null != timeunit;
				Date startedAfter = hasTimeUnit ? getStartDate(timeunit, now) : null;

				Page<JobExecutionRecord> records = jobRecordService.getJobRecords(site.getName(), application,
						jobKey.getName(), startedAfter, now, null, null, new PageRequest(0, pageSize));

				if (hasTimeUnit) {
					jobState.setStartedAfter(toLocalTime(startedAfter));
				}
				jobState.setTotalRecords((int) records.getTotalElements());

				if (withRecords) {
					jobState.setRecords(records.map(r -> toRecord(r)).getContent());
				}

				if (hasTimeUnit) {
					if (hasErrorTreshold && records.getTotalElements() < jobState.getThresholdError()) {
						jobState.setState(JobState.StateEnum.ERROR);
					} else if (hasWarnTreshold && records.getTotalElements() < jobState.getThresholdWarn()) {
						jobState.setState(JobState.StateEnum.WARN);
					} else {
						jobState.setState(JobState.StateEnum.OK);
					}
				} else {
					jobState.setState(JobState.StateEnum.UNDEFINED);
				}

			}
		} catch (SchedulerException e) {
			log.error("error while retrieving job", e);
		}
		return jobState;
	}

	private JobRecord toRecord(JobExecutionRecord r) {
		JobRecord jobRecord = new JobRecord();
		jobRecord.setId(r.getId());
		jobRecord.setStart(toLocalTime(r.getStartTime()));
		jobRecord.setEnd(toLocalTime(r.getEndTime()));
		jobRecord.setRunOnce(r.isRunOnce());
		jobRecord.setDuration(r.getDuration().intValue());
		jobRecord.setStacktrace(r.getStacktraces());
		ExecutionResult execResult = ExecutionResult.valueOf(r.getResult());
		jobRecord.setState(
				ExecutionResult.SUCCESS.equals(execResult) ? JobRecord.StateEnum.OK : JobRecord.StateEnum.ERROR);
		return jobRecord;
	}

	private LocalDateTime toLocalTime(Date date) {
		return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
	}

	public Date getStartDate(TimeunitEnum timeunit, Date now) {
		switch (timeunit) {
		case MINUTE:
			return DateUtils.addMinutes(now, -1);
		case HOUR:
			return DateUtils.addHours(now, -1);
		case WEEK:
			return DateUtils.addWeeks(now, -1);
		case MONTH:
			return DateUtils.addMonths(now, -1);
		case YEAR:
			return DateUtils.addYears(now, -1);
		default:
			return DateUtils.addDays(now, -1);
		}
	}

	boolean isValidBearer(List<String> auths) {
		return null != auths && StringUtils.isNotBlank(bearerToken) && auths.contains("Bearer " + bearerToken);
	}
}
