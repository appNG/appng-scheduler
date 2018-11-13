package org.appng.application.scheduler.quartz;

import java.util.Date;

import org.appng.application.scheduler.model.JobResult;
import org.appng.application.scheduler.service.JobRecordService;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;

public class RecordingJobListener implements JobListener {

	private JobRecordService jobRecordService;
	private boolean enabled;

	public void setJobRecordService(JobRecordService jobRecordService) {
		this.jobRecordService = jobRecordService;
	}

	@Override
	public String getName() {
		return this.getClass().getName();
	}

	@Override
	public void jobToBeExecuted(JobExecutionContext context) {
		// we are not interested on jobs to e executed we just want to get triggered when the job is done
	}

	@Override
	public void jobExecutionVetoed(JobExecutionContext context) {
		// we are not interested on jobs to e executed we just want to get triggered when the job is done
	}

	@Override
	public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
		if (isEnabled()) {
			Object result = context.getResult();
			if (result instanceof JobResult) {
				jobRecordService.recordJob((JobResult) result, context.getFireTime(), new Date(),
						context.getJobRunTime(), context.getJobDetail().getJobDataMap(), jobException,
						context.getTrigger().getJobKey().getName());
			}
		}
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

}
