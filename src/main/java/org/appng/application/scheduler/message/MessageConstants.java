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
package org.appng.application.scheduler.message;

/**
 * All messaging constants are defined here.
 * 
 * @author nilwarn.gajanan, aiticon GmbH
 * 
 */
public class MessageConstants {

	public static final String JOB_CREATED = "job.created";
	public static final String JOB_UPDATED = "job.updated";
	public static final String JOB_UPDATE_ERROR = "job.update.error";
	public static final String JOB_DELETED = "job.deleted";
	public static final String JOB_DELETE_ERROR = "job.delete.error";
	public static final String JOB_NOT_EXISTS_ERROR = "job.not.exists.error";
	public static final String JOB_NOT_NAME_ERROR = "job.not.name.error";
	public static final String JOB_NAME_EXISTS_ERROR = "job.name.exists.error";
	public static final String JOB_RUNNING = "job.running";
	public static final String JOB_RUNNING_ERROR = "job.running.error";
	public static final String JOB_ACTIVE = "job.active";
	public static final String JOB_UNSCHEDULED = "job.unscheduled";
	public static final String JOB_SCHEDULED_EXPR = "job.scheduled.expr";
	public static final String CRONEXPRESSION_INVALID = "job.cronexpression.invalid";

}
