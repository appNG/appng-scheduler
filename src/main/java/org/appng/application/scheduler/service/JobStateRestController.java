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

import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.appng.api.ScheduledJobResult.ExecutionResult;
import org.appng.api.model.Application;
import org.appng.api.model.Site;
import org.appng.core.domain.JobExecutionRecord;
import org.appng.scheduler.openapi.model.JobRecord;
import org.appng.scheduler.openapi.model.JobState;
import org.appng.scheduler.openapi.model.JobState.StateEnum;
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

	public static final String THRESHOLD_TIMEUNIT = "thresholdTimeunit";
	public static final String THRESHOLD_ERROR = "thresholdError";
	public static final String THRESHOLD_WARN = "thresholdWarn";
	private JobRecordService jobRecordService;
	private Scheduler scheduler;
	@Value("${bearerToken}")
	private String bearerToken;

	public JobStateRestController(JobRecordService jobRecordService, Scheduler scheduler) {
		this.jobRecordService = jobRecordService;
		this.scheduler = scheduler;
	}

	@RequestMapping(value = "/jobRecords", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
	public ResponseEntity<List<JobRecord>> getJobRecords(
			@RequestParam(required = false, name = "application") String applicationName,
			@RequestParam(required = false, name = "job") String jobName,
			@RequestParam(required = false, name = "startedAfter") String startedAfter,
			@RequestParam(required = false, name = "startedBefore") String startedBefore,
			@RequestParam(required = false, name = "result") String result,
			@RequestParam(required = false, name = "minDuration") Integer duration,
			@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) List<String> auths,
			Application application, Site site) {
		if (!isValidBearer(auths)) {
			return new ResponseEntity<>(null, HttpStatus.UNAUTHORIZED);
		}

		// TODO

//		Page<JobExecutionRecord> records = jobRecordService.getJobRecords(site.getName(), application,
//				jobName, startedAfter, now, null, null, new PageRequest(0, 10));
//		return new ResponseEntity<>(records.map(r->toRecord(r)).getContent(), HttpStatus.OK);
		return null;
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
			if (null == jobState.getStartedAfter()) {
				return ResponseEntity.notFound().build();
			}
			if (!withRecords) {
				jobState.setRecords(null);
			}
			return ResponseEntity.ok(jobState);
		}
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
	}

	private JobState getJobState(String application, String job, Site site, Integer pageSize, boolean withRecords) {
		JobState jobState = new JobState();
		jobState.setSite(site.getName());
		jobState.setApplication(application);
		String jobName = job.startsWith(application) ? job : application + "_" + job;
		jobState.setJob(jobName);
		try {
			JobKey jobKey = new JobKey(jobName, site.getName());
			JobDetail jobDetail = scheduler.getJobDetail(jobKey);
			if (null == jobDetail) {
				jobState.setState(StateEnum.UNDEFINED);
			} else {
				JobDataMap jobDataMap = jobDetail.getJobDataMap();
				boolean hasWarnTreshold = jobDataMap.containsKey(THRESHOLD_WARN);
				if (hasWarnTreshold) {
					jobState.setThresholdWarn(jobDataMap.getInt(THRESHOLD_WARN));
				}
				boolean hasErrorTreshold = jobDataMap.containsKey(THRESHOLD_ERROR);
				if (hasErrorTreshold) {
					jobState.setThresholdError(jobDataMap.getInt(THRESHOLD_ERROR));
				}
				if (jobDataMap.containsKey(THRESHOLD_TIMEUNIT)) {
					jobState.setTimeunit(TimeunitEnum.valueOf(jobDataMap.getString(THRESHOLD_TIMEUNIT).toUpperCase()));
				}

				Date now = new Date();
				Date startedAfter = getStartDate(jobState.getTimeunit(), now);

				Page<JobExecutionRecord> records = jobRecordService.getJobRecords(site.getName(), application,
						jobKey.getName(), startedAfter, now, null, null, new PageRequest(0, pageSize));

				jobState.setStartedAfter(startedAfter.toInstant().atOffset(ZoneOffset.UTC));
				jobState.setTotalRecords((int) records.getTotalElements());

				if (withRecords) {
					jobState.setRecords(records.map(r -> toRecord(r)).getContent());
				}

				if (hasErrorTreshold && records.getTotalElements() < jobState.getThresholdError()) {
					jobState.setState(StateEnum.ERROR);
				} else if (hasWarnTreshold && records.getTotalElements() < jobState.getThresholdWarn()) {
					jobState.setState(StateEnum.WARN);
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
		jobRecord.setStart(r.getStartTime().toInstant().atOffset(ZoneOffset.UTC));
		jobRecord.setEnd(r.getEndTime().toInstant().atOffset(ZoneOffset.UTC));
		jobRecord.setRunOnce(r.isRunOnce());
		jobRecord.setDuration(r.getDuration().intValue());
		jobRecord.setStacktrace(r.getStacktraces());
		ExecutionResult execResult = ExecutionResult.valueOf(r.getResult());
		jobRecord.setState(
				ExecutionResult.SUCCESS.equals(execResult) ? org.appng.scheduler.openapi.model.JobRecord.StateEnum.OK
						: org.appng.scheduler.openapi.model.JobRecord.StateEnum.ERROR);
		return jobRecord;
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
