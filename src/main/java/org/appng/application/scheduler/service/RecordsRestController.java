package org.appng.application.scheduler.service;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.appng.api.model.Application;
import org.appng.api.model.Site;
import org.appng.application.scheduler.model.JobRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RecordsRestController {

	@Autowired
	private JobRecordService jobRecordService;

	@RequestMapping(value = "/jobRecords", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
	public ResponseEntity<?> getJobRecords(@RequestParam(required = false, name = "application") String applicationName,
			@RequestParam(required = false, name = "job") String jobName,
			@RequestParam(required = false, name = "startedAfter") String startedAfter,
			@RequestParam(required = false, name = "startedBefore") String startedBefore,
			@RequestParam(required = false, name = "result") String result,
			@RequestParam(required = false, name = "minDuration") String duration,
			@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) List<String> auths,
			Application application, Site site) {
		if (!verifyToken(application, auths)) {
			return new ResponseEntity<>(null, HttpStatus.UNAUTHORIZED);
		}

		List<JobRecord> records = jobRecordService.getRecords(site.getName(), applicationName, jobName, startedAfter,
				startedBefore, result, duration);

		return new ResponseEntity<>(records, HttpStatus.OK);

	}

	boolean verifyToken(Application application, List<String> auths) {
		if (null == auths) {
			return false;
		}
		String token = application.getProperties().getString("bearerToken");
		if (StringUtils.isNotBlank(token) && !auths.contains("Bearer " + token)) {
			return false;
		}
		return true;
	}

	public JobRecordService getJobRecordService() {
		return jobRecordService;
	}

	public void setJobRecordService(JobRecordService jobRecordService) {
		this.jobRecordService = jobRecordService;
	}

}
