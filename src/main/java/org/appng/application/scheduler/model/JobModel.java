/*
 * Copyright 2011-2017 the original author or authors.
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

import org.apache.commons.lang3.StringUtils;
import org.appng.api.model.Named;

public class JobModel implements Named<String>, Comparable<Named<String>> {

	private String name;
	private String availableJob;
	private String description;
	private String origin;
	private String jobClass;
	private boolean running;
	private String cronExpression;
	private String jobDataMap;
	private Date nextFireTime;
	private Date previousFireTime;

	public String getAvailableJob() {
		if (StringUtils.isBlank(availableJob)) {
			return name;
		} else {
			return availableJob;
		}
	}

	public void setAvailableJob(String availableJob) {
		this.availableJob = availableJob;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getOrigin() {
		return origin;
	}

	public void setOrigin(String origin) {
		this.origin = origin;
	}

	public String getJobClass() {
		return jobClass;
	}

	public void setJobClass(String jobClass) {
		this.jobClass = jobClass;
	}

	public boolean isRunning() {
		return running;
	}

	public void setRunning(boolean running) {
		this.running = running;
	}

	public String getCronExpression() {
		return cronExpression;
	}

	public void setCronExpression(String cronExpression) {
		this.cronExpression = cronExpression;
	}

	public Date getNextFireTime() {
		return nextFireTime;
	}

	public void setNextFireTime(Date nextFireTime) {
		this.nextFireTime = nextFireTime;
	}

	public Date getPreviousFireTime() {
		return previousFireTime;
	}

	public void setPreviousFireTime(Date previousFireTime) {
		this.previousFireTime = previousFireTime;
	}

	public String getId() {
		return getName();
	}

	public Date getVersion() {
		return null;
	}

	public int compareTo(Named<String> other) {
		return getName().compareTo(other.getName());
	}

}
