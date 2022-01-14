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
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.collections4.EnumerationUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.appng.api.Environment;
import org.appng.api.RequestUtil;
import org.appng.api.ScheduledJobResult.ExecutionResult;
import org.appng.api.SiteProperties;
import org.appng.api.model.Application;
import org.appng.api.model.Site;
import org.appng.application.scheduler.Constants;
import org.appng.application.scheduler.PropertyConstants;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.collect.Lists;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link RestController} providing a REST API to query for {@link JobState}s
 * 
 * @author Matthias Müller
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class JobStateRestController implements JobStateApi {

	private final JobRecordService jobRecordService;
	private final Scheduler scheduler;
	private final Site site;
	private final Application app;
	private final Environment env;
	private @Autowired HttpServletRequest request;
	private @Value("${" + PropertyConstants.BEARER_TOKEN + "}") String bearerToken;
	private @Value("${skipAuth:false}") boolean skipAuth;

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

	public HttpServletRequest getRequest() {
		return request;
	}

	@Override
	public ResponseEntity<Jobs> getJobs(
			@RequestParam(value = "jobdata", required = false, defaultValue = "false") Boolean addJobdata,
			@RequestParam(value = "all", required = false, defaultValue = "false") Boolean addAll,
			@RequestParam(value = "thresholds", required = false) Boolean thresholds) {
		if (!isAuthorized()) {
			return new ResponseEntity<>(null, HttpStatus.UNAUTHORIZED);
		}
		List<Job> jobList = Lists.newArrayList();
		if (addAll) {
			for (String siteName : new TreeSet<>(RequestUtil.getSiteNames(env))) {
				Site site = RequestUtil.getSiteByName(env, siteName);
				if (site.isActive()) {
					try {
						Application schedulerApp = site.getApplication(app.getName());
						if (null != schedulerApp) {
							addJobs(addJobdata, thresholds, jobList, site, schedulerApp);
						}
					} catch (SchedulerException e) {
						log.error("error while retrieving jobs for site " + site.getName(), e);
					}
				}
			}
		} else {
			try {
				addJobs(addJobdata, thresholds, jobList, site, app);
			} catch (SchedulerException e) {
				log.error("error while retrieving jobs for site " + site.getName(), e);
			}
		}

		return ResponseEntity.ok(new Jobs().jobs(jobList));
	}

	protected void addJobs(Boolean addJobdata, Boolean thresholds, List<Job> jobList, Site site,
			Application schedulerApp) throws SchedulerException {
		Scheduler siteScheduler = schedulerApp.getBean(Scheduler.class);
		Set<JobKey> jobKeys = siteScheduler.getJobKeys(GroupMatcher.jobGroupEquals(site.getName()));
		for (JobKey jobKey : jobKeys) {
			JobDetail jobDetail = siteScheduler.getJobDetail(jobKey);
			JobDataMap jobDataMap = jobDetail.getJobDataMap();
			Boolean thresholdsPresent = jobDataMap.containsKey(Constants.THRESHOLD_TIMEUNIT)
					&& (jobDataMap.containsKey(Constants.THRESHOLD_WARN)
							|| jobDataMap.containsKey(Constants.THRESHOLD_ERROR));

			if (null == thresholds || thresholdsPresent.equals(thresholds)) {
				Job job = new Job();
				job.setSite(site.getName());
				String name = jobKey.getName();
				String[] splittedName = name.split("_");
				job.setApplication(splittedName[0]);
				job.setJob(splittedName[1]);
				String servicePath = site.getProperties().getString(SiteProperties.SERVICE_PATH);
				String detail = String.format("%s%s/%s/appng-scheduler/rest/jobState/%s/%s", site.getDomain(),
						servicePath, site.getName(), job.getApplication(), job.getJob());
				job.setSelf(detail);

				if (addJobdata) {
					job.setJobData(jobDataMap.getWrappedMap());
				}

				job.setThresholdsPresent(thresholdsPresent);
				jobList.add(job);
			}
		}
	}

	@Override
	public ResponseEntity<JobState> getJobState(
	// @formatter:off
			@PathVariable("application") String application,
			@PathVariable("job") String job,
			@RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize,
			@RequestParam(value = "records", required = false, defaultValue = "false") Boolean records
	// @formatter:on
	) {
		if (!isAuthorized()) {
			return new ResponseEntity<>(null, HttpStatus.UNAUTHORIZED);
		}

		JobState jobState = getState(application, job, pageSize, records);
		if (null != jobState) {
			return ResponseEntity.ok(jobState);
		}
		return ResponseEntity.notFound().build();
	}

	private JobState getState(String application, String job, Integer pageSize, boolean withRecords) {
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
			return false;
		}
		List<String> auths = EnumerationUtils.toList(getRequest().getHeaders(HttpHeaders.AUTHORIZATION));
		return null != auths && auths.contains("Bearer " + bearerToken);
	}

	protected List<String> getCleanedList(String value) {
		return Arrays.asList(StringUtils.trimToEmpty(value).split(",")).stream().map(StringUtils::trim)
				.collect(Collectors.toList());
	}
}
