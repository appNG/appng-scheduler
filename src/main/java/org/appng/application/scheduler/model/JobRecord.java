package org.appng.application.scheduler.model;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

public class JobRecord extends JobResult {

	private Date start;
	private Date end;
	private Long duration;
	private String stacktraces;

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd@HH:mm:ss.SSSZ")
	public Date getStart() {
		return start;
	}

	public void setStart(Date start) {
		this.start = start;
	}

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd@HH:mm:ss.SSSZ")
	public Date getEnd() {
		return end;
	}

	public void setEnd(Date end) {
		this.end = end;
	}

	@JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT)
	public Long getDuration() {
		return duration;
	}

	public void setDuration(Long duration) {
		this.duration = duration;
	}

	public String getStacktraces() {
		return stacktraces;
	}

	public void setStacktraces(String stacktraces) {
		this.stacktraces = stacktraces;
	}
}
