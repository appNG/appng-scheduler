/*
 * Copyright 2011-2023 the original author or authors.
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
import org.apache.commons.lang3.time.FastDateFormat;
import org.appng.api.Environment;
import org.appng.api.Request;
import org.appng.api.RequestUtil;
import org.appng.api.SiteProperties;
import org.appng.api.ScheduledJobResult.ExecutionResult;
import org.appng.api.model.Application;
import org.appng.api.model.Site;
import org.appng.api.model.Site.SiteState;
import org.appng.api.support.environment.DefaultEnvironment;
import org.appng.application.scheduler.Constants;
import org.appng.application.scheduler.PropertyConstants;
import org.appng.application.scheduler.SchedulerUtils;
import org.appng.core.controller.HttpHeaders;
import org.appng.scheduler.openapi.JobStateApi;
import org.appng.scheduler.openapi.model.Job;
import org.appng.scheduler.openapi.model.JobRecord;
import org.appng.scheduler.openapi.model.JobState;
import org.appng.scheduler.openapi.model.Jobs;
import org.appng.scheduler.openapi.model.JobState.StateNameEnum;
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

	private static final FastDateFormat DATE_FORMAT = FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
	private final JobRecordService jobRecordService;
	private final Scheduler scheduler;
	private final Site site;
	private final Application app;
	private @Value("${" + PropertyConstants.BEARER_TOKEN + "}") String bearerToken;
	private @Value("${skipAuth:false}") boolean skipAuth;
	private @Value("${checkFirstRun:true}") boolean checkFirstRun;

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
	protected Request getRequest() {
		return null;
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
			Environment env = getRequest().getEnvironment();
			for (String siteName : new TreeSet<>(RequestUtil.getSiteNames(env))) {
				Site site = RequestUtil.getSiteByName(env, siteName);
				if (site.isActive() && SiteState.STARTED.equals(site.getState())) {
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
				String application = name.substring(0, name.indexOf(SchedulerUtils.JOB_SEPARATOR));
				job.setApplication(application);
				job.setJob(name.substring(application.length() + 1, name.length()));
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
			String jobName = job.startsWith(application) ? job : application + SchedulerUtils.JOB_SEPARATOR + job;
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

				Page<org.appng.core.domain.JobRecord> okRecords = jobRecordService.getJobRecords(site.getName(), application,
						jobKey.getName(), startedAfter, now, ExecutionResult.SUCCESS.name(), null,
						new PageRequest(0, 1));

				Page<org.appng.core.domain.JobRecord> failedRecords = jobRecordService.getJobRecords(site.getName(), application,
						jobKey.getName(), startedAfter, now, ExecutionResult.FAIL.name(), null, new PageRequest(0, 1));

				if (hasTimeUnit) {
					jobState.setStartedAfter(toOffsetDateTime(startedAfter));
				}
				int totalSuccess = (int) okRecords.getTotalElements();
				int totalFailed = (int) failedRecords.getTotalElements();
				int total = totalSuccess + totalFailed;

				jobState.setTotalFailed(totalFailed);
				jobState.setTotalSuccess(totalSuccess);
				jobState.setTotalRecords(total);

				if (withRecords) {
					Page<org.appng.core.domain.JobRecord> allRecords = jobRecordService.getJobRecords(site.getName(), application,
							jobKey.getName(), startedAfter, now, null, null, new PageRequest(0, pageSize));
					jobState.setRecords(allRecords.map(r -> toRecord(r)).getContent());
				}

				String message = "Thresholds and/or time unit have not been definded.";
				boolean hasErrorThreshold = thresholdError > 0;
				boolean hasWarnThreshold = thresholdWarn > 0;
				StateNameEnum state = StateNameEnum.OK;

				if (jobDataMap.getBoolean(Constants.THRESHOLDS_DISABLED)) {
					message = "Thresholds are disabled";
				} else if (hasTimeUnit && (hasWarnThreshold || hasErrorThreshold)) {
					org.appng.core.domain.JobRecord firstRun = jobRecordService.getFirstRun(site.getName(), application,
							jobKey.getName());
					if (checkFirstRun && null != firstRun && firstRun.getStartTime().after(startedAfter)) {
						message = String.format(
								"Job first run at %s, so not enough data to validate tresholds based on %s.",
								DATE_FORMAT.format(firstRun.getStartTime()), DATE_FORMAT.format(startedAfter));
					} else {
						int treshold;
						String operand = "less than";
						StateNameEnum logState = state;
						String messageFormat = "The job failed %s time(s) and succeeded %s time(s) during the last %s, which is %s the %s treshold of %s.";
						if (hasErrorThreshold && totalSuccess < thresholdError) {
							logState = state = StateNameEnum.ERROR;
							treshold = thresholdError;
						} else if (hasWarnThreshold && totalSuccess < thresholdWarn) {
							logState = state = StateNameEnum.WARN;
							treshold = thresholdWarn;
						} else {
							treshold = hasWarnThreshold ? thresholdWarn : (hasErrorThreshold ? thresholdError : -1);
							logState = hasWarnThreshold ? StateNameEnum.WARN
									: (hasErrorThreshold ? StateNameEnum.ERROR : StateNameEnum.OK);
							operand = "greater than/equal to";
						}
						message = String.format(messageFormat, totalFailed, totalSuccess, timeunit, operand, logState,
								treshold);
					}
				}
				jobState.setStateName(state);
				jobState.setState(state.ordinal());
				jobState.setMessage(message);
			}
		} catch (SchedulerException e) {
			log.error("error while retrieving job", e);
		}
		return jobState;
	}

	private JobRecord toRecord(org.appng.core.domain.JobRecord r) {
		JobRecord jobRecord = new JobRecord();
		jobRecord.setId(r.getId());
		jobRecord.setStart(toOffsetDateTime(r.getStartTime()));
		jobRecord.setEnd(toOffsetDateTime(r.getEndTime()));
		jobRecord.setRunOnce(r.isRunOnce());
		jobRecord.setDuration(r.getDuration().intValue());
		jobRecord.setNode(r.getNode());
		ExecutionResult execResult = ExecutionResult.valueOf(r.getResult());
		jobRecord.setState(
				ExecutionResult.SUCCESS.equals(execResult) ? JobRecord.StateEnum.OK : JobRecord.StateEnum.ERROR);
		return jobRecord;
	}

	private OffsetDateTime toOffsetDateTime(Date date) {
		return date.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
	}

	boolean isAuthorized() {
		if (skipAuth) {
			return true;
		}
		if (StringUtils.isBlank(bearerToken)) {
			return false;
		}
		DefaultEnvironment env = (DefaultEnvironment) getRequest().getEnvironment();
		HttpServletRequest servletRequest = env.getServletRequest();
		List<String> auths = EnumerationUtils.toList(servletRequest.getHeaders(HttpHeaders.AUTHORIZATION));
		return null != auths && auths.contains("Bearer " + bearerToken);
	}

	protected List<String> getCleanedList(String value) {
		return Arrays.asList(StringUtils.trimToEmpty(value).split(",")).stream().map(StringUtils::trim)
				.collect(Collectors.toList());
	}
}
