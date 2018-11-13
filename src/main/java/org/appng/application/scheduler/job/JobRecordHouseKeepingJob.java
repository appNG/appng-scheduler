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

	@Override
	public String getDescription() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setDescription(String description) {
		// TODO Auto-generated method stub

	}

	@Override
	public Map<String, Object> getJobDataMap() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setJobDataMap(Map<String, Object> map) {
		// TODO Auto-generated method stub

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

	public ScheduledJobResult getResult() {
		return result;
	}

	public void setResult(ScheduledJobResult result) {
		this.result = result;
	}

}
