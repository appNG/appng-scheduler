package org.appng.application.scheduler.model;

import java.util.Date;

public class JobRecord extends JobResult {

	private Date start;
	private Date end;
	private Long duration;

	public Date getStart() {
		return start;
	}

	public void setStart(Date start) {
		this.start = start;
	}

	public Date getEnd() {
		return end;
	}

	public void setEnd(Date end) {
		this.end = end;
	}

	public Long getDuration() {
		return duration;
	}

	public void setDuration(Long duration) {
		this.duration = duration;
	}
}
