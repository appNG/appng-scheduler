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

import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.appng.api.ScheduledJobResult.ExecutionResult;
import org.appng.api.model.Application;
import org.appng.api.model.Site;
import org.appng.application.scheduler.Constants;
import org.appng.application.scheduler.PropertyConstants;
import org.appng.application.scheduler.model.JobRecord;
import org.appng.application.scheduler.model.JobResult;
import org.appng.core.domain.JobExecutionRecord;
import org.appng.core.repository.JobExecutionRecordRepository;
import org.appng.persistence.repository.SearchQuery;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service class to deal with saving, deleting and querying for saved job
 * execution records.
 * 
 * @author Claus Stümke
 */
@Component
@Transactional
public class JobRecordService {

	private static final String FIELD_RESULT = "result";
	private static final String FIELD_DURATION = "duration";
	private static final String FIELD_START = "startTime";
	private static final String FIELD_JOB_NAME = "jobName";
	private static final String FIELD_SITE = "site";
	private static final String FIELD_APPLICATION = "application";

	private JobExecutionRecordRepository recordRepository;

	public JobRecordService(JobExecutionRecordRepository recordRepository) {
		this.recordRepository = recordRepository;
	}

	public void recordJob(JobResult jobResult, Date fireTime, Date endTime, long jobRunTime, JobDataMap jobDataMap,
			JobExecutionException jobException, String triggerName) {

		ExecutionResult result = getResult(jobResult, jobException);
		JobExecutionRecord record = new JobExecutionRecord();
		record.setApplication(jobResult.getApplicationName());
		record.setSite(jobResult.getSiteName());
		record.setJobName(jobResult.getJobName());
		record.setStartTime(fireTime);
		record.setEndTime(endTime);
		record.setDuration(Long.valueOf(jobRunTime / 1000).intValue());
		record.setRunOnce(jobDataMap.getBoolean(Constants.JOB_RUN_ONCE));
		record.setResult(result.name());
		record.setStacktraces(null == jobException ? null : ExceptionUtils.getStackTrace(jobException));
		record.setCustomData(jobResult.getCustomData());
		record.setTriggername(triggerName);

		recordRepository.save(record);
	}

	private ExecutionResult getResult(JobResult jobResult, JobExecutionException jobException) {
		// if the result is set, take it otherwise set it depending on the existence of
		// an exception
		if (null != jobResult.getResult()) {
			return jobResult.getResult();
		} else if (null == jobException) {
			return ExecutionResult.SUCCESS;
		}
		return ExecutionResult.FAIL;
	}

	public List<String> getDistinctJobNames(String siteName) {
		return recordRepository.getDistinctJobNames(siteName);
	}
	
	public List<String> getDistinctApplications(String siteName) {
		return recordRepository.getDistinctApplications(siteName);
	}

	public Page<JobRecord> getRecords(String siteName, String applicationFilter, String jobFilter, Date start,
			Date end, String result, Integer duration, Pageable pageable) {

		SearchQuery<JobExecutionRecord> query = recordRepository.createSearchQuery();
		query.equals(FIELD_SITE, siteName);
		query.equals(FIELD_APPLICATION, applicationFilter);
		query.equals(FIELD_JOB_NAME, StringUtils.trimToNull(jobFilter));
		query.equals(FIELD_RESULT, StringUtils.trimToNull(result));
		query.greaterEquals(FIELD_START, start);
		query.lessEquals(FIELD_START, end);
		query.greaterEquals(FIELD_DURATION, duration);

		return recordRepository.search(query, pageable).map(r -> JobRecord.fromDomain(r));
	}

	public Integer cleanUp(Site site, Application application) {
		if (StringUtils.isNoneBlank(application.getProperties().getString(PropertyConstants.RECORD_LIFE_TIME))) {
			Integer lifetime = application.getProperties().getInteger(PropertyConstants.RECORD_LIFE_TIME);

			Date outdated = DateUtils.addDays(new Date(), -lifetime);

			SearchQuery<JobExecutionRecord> query = recordRepository.createSearchQuery()
					.equals(FIELD_SITE, site.getName()).lessEquals(FIELD_START, outdated);
			Page<JobExecutionRecord> outdatedRecords = recordRepository.search(query, null);
			recordRepository.delete(outdatedRecords);

			return (int) outdatedRecords.getTotalElements();
		}
		return 0;
	}

	public JobRecord getRecord(Integer recordId) {
		return JobRecord.fromDomain(recordRepository.findOne(recordId));
	}

}
