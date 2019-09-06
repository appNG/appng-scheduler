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

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.appng.api.model.Application;
import org.appng.api.model.Site;
import org.appng.application.scheduler.PropertyConstants;
import org.appng.application.scheduler.business.Records;
import org.appng.application.scheduler.model.JobRecord;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@link RestController} providing a REST API to query for job records.
 * 
 * @author Claus Stümke
 *
 */
@RestController
public class RecordsRestController {

	private JobRecordService jobRecordService;

	public RecordsRestController(JobRecordService jobRecordService) {
		this.jobRecordService = jobRecordService;
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
		if (!verifyToken(application, auths)) {
			return new ResponseEntity<>(null, HttpStatus.UNAUTHORIZED);
		}

		Page<JobRecord> records = jobRecordService.getRecords(site.getName(), applicationName, jobName, Records.getDate(startedAfter),
				 Records.getDate(startedBefore), result, duration, null);
		return new ResponseEntity<>(records.getContent(), HttpStatus.OK);

	}

	boolean verifyToken(Application application, List<String> auths) {
		if (null == auths) {
			return false;
		}
		String token = application.getProperties().getString(PropertyConstants.BEARER_TOKEN);
		return StringUtils.isNotBlank(token) && auths.contains("Bearer " + token);
	}

}
