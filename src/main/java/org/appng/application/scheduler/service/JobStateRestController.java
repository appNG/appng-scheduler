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

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.EnumerationUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.appng.api.ScheduledJobResult.ExecutionResult;
import org.appng.api.model.Site;
import org.appng.application.scheduler.Constants;
import org.appng.core.controller.HttpHeaders;
import org.appng.core.domain.JobExecutionRecord;
import org.appng.scheduler.openapi.JobStateApi;
import org.appng.scheduler.openapi.model.Job;
import org.appng.scheduler.openapi.model.JobRecord;
import org.appng.scheduler.openapi.model.JobState;
import org.appng.scheduler.openapi.model.Jobs;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.collect.Lists;

import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link RestController} providing a REST API to query for {@link JobState}s
 * 
 * @author Matthias Müller
 */
@Slf4j
@RestController
public class JobStateRestController implements JobStateApi {

	private JobRecordService jobRecordService;
	private Scheduler scheduler;
	private Site site;
	private @Value("${bearerToken}") String bearerToken;
	private @Value("${skipAuth:false}") boolean skipAuth;
	private @Value("${platform.schedulerStateWhitelist:127.0.0.1}") String schedulerStateWhitelist;

	public JobStateRestController(JobRecordService jobRecordService, Scheduler scheduler, Site site) {
		this.jobRecordService = jobRecordService;
		this.scheduler = scheduler;
		this.site = site;
	}

	public enum TimeUnit {
		YEAR, MONTH, WEEK, DAY, HOUR, MINUTE;

		public Date getStartDate(Date now) {
			switch (this) {
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
	}

	@Lookup
	public HttpServletRequest getRequest() {
		return null;
	}

	@Override
	public ResponseEntity<Jobs> getJobs() {
		if (!isAuthorized()) {
			return new ResponseEntity<>(null, HttpStatus.UNAUTHORIZED);
		}
		List<Job> jobList = Lists.newArrayList();
		try {
			Set<JobKey> jobKeys = scheduler.getJobKeys(GroupMatcher.jobGroupEquals(site.getName()));
			for (JobKey jobKey : jobKeys) {

				JobDetail jobDetail = scheduler.getJobDetail(jobKey);
				JobDataMap jobDataMap = jobDetail.getJobDataMap();

				Job job = new Job();
				job.setSite(site.getName());
				String name = jobKey.getName();
				String[] splittedName = name.split("_");
				job.setApplication(splittedName[0]);
				job.setJob(name.split("_")[1]);
				String detail = getRequest().getRequestURL().toString().replace("/list",
						String.format("/%s/%s", job.getApplication(), job.getJob()));
				job.setSelf(detail);
				job.setJobData(jobDataMap.getWrappedMap());
				jobList.add(job);
			}
		} catch (SchedulerException e) {
			log.error("error while retrieving job", e);
		}
		Jobs jobs = new Jobs();
		jobs.setJobs(jobList);
		return ResponseEntity.ok(jobs);
	}

	@Override
	public ResponseEntity<JobState> getJobState(
			@ApiParam(value = "the site to call", required = true) @PathVariable("application") String application,
			@ApiParam(value = "the application to call", required = true) @PathVariable("job") String job,
			@ApiParam(value = "site of the page", defaultValue = "10") @Valid @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize,
			@ApiParam(value = "show records?", defaultValue = "false") @Valid @RequestParam(value = "records", required = false, defaultValue = "false") Boolean records) {
		if (!isAuthorized()) {
			return new ResponseEntity<>(null, HttpStatus.UNAUTHORIZED);
		}

		JobState jobState = getJobState(application, job, site, pageSize, records);
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
				jobState.setJobData(jobDataMap.getWrappedMap());

				int thresholdWarn = -1;
				int thresholdError = -1;
				if (jobDataMap.containsKey(Constants.THRESHOLD_WARN)) {
					thresholdWarn = jobDataMap.getInt(Constants.THRESHOLD_WARN);
				}
				if (jobDataMap.containsKey(Constants.THRESHOLD_ERROR)) {
					thresholdError = jobDataMap.getInt(Constants.THRESHOLD_ERROR);
				}

				TimeUnit timeunit = null;
				if (jobDataMap.containsKey(Constants.THRESHOLD_TIMEUNIT)) {
					timeunit = TimeUnit.valueOf(jobDataMap.getString(Constants.THRESHOLD_TIMEUNIT).toUpperCase());
				}

				Date now = new Date();
				boolean hasTimeUnit = null != timeunit;
				Date startedAfter = hasTimeUnit ? timeunit.getStartDate(now) : null;

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
					if (thresholdError > 0 && records.getTotalElements() < thresholdError) {
						jobState.setState(JobState.StateEnum.ERROR);
					} else if (thresholdWarn > 0 && records.getTotalElements() < thresholdWarn) {
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

	private OffsetDateTime toLocalTime(Date date) {
		return date.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
	}

	boolean isAuthorized() {
		if (skipAuth) {
			return true;
		}
		if (StringUtils.isBlank(bearerToken)) {
			List<String> forwardedFor = getCleanedList(getRequest().getHeader(HttpHeaders.X_FORWARDED_FOR));
			List<String> whiteListIps = getCleanedList(schedulerStateWhitelist);
			Collection<String> allowedIps = CollectionUtils.intersection(forwardedFor, whiteListIps);
			if (log.isDebugEnabled()) {
				log.debug("Request was forwarded for {}, whitelist: {}", StringUtils.join(forwardedFor),
						StringUtils.join(whiteListIps));
			}
			return !allowedIps.isEmpty();
		} else {
			List<String> auths = EnumerationUtils.toList(getRequest().getHeaders(HttpHeaders.AUTHORIZATION));
			return null != auths && StringUtils.isNotBlank(bearerToken) && auths.contains("Bearer " + bearerToken);
		}
	}

	protected List<String> getCleanedList(String value) {
		return Arrays.asList(StringUtils.trimToEmpty(value).split(",")).stream().map(StringUtils::trim)
				.collect(Collectors.toList());
	}
}
