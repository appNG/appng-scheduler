package org.appng.application.scheduler.job;

import java.util.Map;

import org.appng.api.ScheduledJob;
import org.appng.api.ScheduledJobResult;
import org.appng.api.ScheduledJobResult.ExecutionResult;
import org.appng.api.model.Application;
import org.appng.api.model.Site;
import org.appng.application.scheduler.service.JobRecordService;

public class JobRecordHouseKeepingJob implements ScheduledJob {

	private JobRecordService jobRecordService;

	private ScheduledJobResult result;

	private Map<String, Object> jobDataMap;

	private String description;

	public Map<String, Object> getJobDataMap() {
		return jobDataMap;
	}

	public void setJobDataMap(Map<String, Object> jobDataMap) {
		this.jobDataMap = jobDataMap;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	@Override
	public void execute(Site site, Application application) throws Exception {
		String details = jobRecordService.cleanUp(site, application);
		this.result = new ScheduledJobResult();
		this.result.setResult(ExecutionResult.SUCCESS);
		this.result.setCustomData(details);

	}

	public JobRecordService getJobRecordService() {
		return jobRecordService;
	}

	public void setJobRecordService(JobRecordService jobRecordService) {
		this.jobRecordService = jobRecordService;
	}

	@Override
	public ScheduledJobResult getResult() {
		return result;
	}

	public void setResult(ScheduledJobResult result) {
		this.result = result;
	}

}
