package org.appng.application.scheduler.model;

import org.appng.api.ScheduledJobResult;
import org.appng.api.ScheduledJobResult.ExecutionResult;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class JobResult {
	private String applicationName;
	private String siteName;
	private String jobName;
	private ScheduledJobResult scheduledJobResult;

	public JobResult() {

	}

	public JobResult(ScheduledJobResult result, String application, String site, String name) {
		this.setScheduledJobResult(result);
		this.applicationName = application;
		this.siteName = site;
		this.jobName = name;
	}

	public String getApplicationName() {
		return applicationName;
	}

	public String getSiteName() {
		return siteName;
	}

	public String getJobName() {
		return jobName;
	}

	public void setApplicationName(String applicationName) {
		this.applicationName = applicationName;
	}

	public void setSiteName(String siteName) {
		this.siteName = siteName;
	}

	public void setJobName(String jobName) {
		this.jobName = jobName;
	}

	@JsonIgnore
	public ScheduledJobResult getScheduledJobResult() {
		return scheduledJobResult;
	}

	public void setScheduledJobResult(ScheduledJobResult scheduledJobResult) {
		this.scheduledJobResult = scheduledJobResult;
	}

	public ExecutionResult getResult() {
		if (null != scheduledJobResult) {
			return scheduledJobResult.getResult();
		}
		return null;
	}

	public String getCustomData() {
		if (null != scheduledJobResult) {
			return scheduledJobResult.getCustomData();
		}
		return null;
	}

}
