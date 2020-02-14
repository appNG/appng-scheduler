/*
 * Copyright 2011-2020 the original author or authors.
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

import java.io.Serializable;

import org.appng.api.ScheduledJobResult;
import org.appng.api.ScheduledJobResult.ExecutionResult;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

@SuppressWarnings("serial")
@Data
public class JobResult implements Serializable {

	private String applicationName;
	private String siteName;
	private String jobName;
	private String triggerName;
	private boolean runOnce;

	@JsonIgnore
	private ScheduledJobResult scheduledJobResult;

	public JobResult() {

	}

	public JobResult(ScheduledJobResult result, String application, String site, String name) {
		this.setScheduledJobResult(result);
		this.applicationName = application;
		this.siteName = site;
		this.jobName = name;
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
