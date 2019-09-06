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
package org.appng.application.scheduler.model;

import java.util.Date;

import org.appng.api.ScheduledJobResult;
import org.appng.api.ScheduledJobResult.ExecutionResult;
import org.appng.core.domain.JobExecutionRecord;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/**
 * Model class representing a row in the job record table.
 * 
 * @author Claus Stümke
 */
@Data
public class JobRecord extends JobResult {

	@JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
	private Integer id;

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd@HH:mm:ss.SSSZ")
	private Date start;

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd@HH:mm:ss.SSSZ")
	private Date end;

	@JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT)
	private Long duration;

	private String stacktraces;

	public static JobRecord fromDomain(JobExecutionRecord r) {
		JobRecord jobRecord = new JobRecord();
		jobRecord.setId(r.getId());
		jobRecord.setApplicationName(r.getApplication());
		jobRecord.setSiteName(r.getSite());
		jobRecord.setJobName(r.getJobName());
		jobRecord.setTriggerName(r.getTriggername());
		jobRecord.setStart(r.getStartTime());
		jobRecord.setEnd(r.getEndTime());
		jobRecord.setRunOnce(r.isRunOnce());
		jobRecord.setDuration(r.getDuration().longValue());
		jobRecord.setStacktraces(r.getStacktraces());
		ScheduledJobResult scheduledJobResult = new ScheduledJobResult();
		scheduledJobResult.setResult(ExecutionResult.valueOf(r.getResult()));
		scheduledJobResult.setCustomData(r.getCustomData());
		jobRecord.setScheduledJobResult(scheduledJobResult);
		return jobRecord;
	}

}
