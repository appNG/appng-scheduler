/*
 * Copyright 2011-2018 the original author or authors.
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
package org.appng.application.scheduler.quartz;

import java.util.Properties;

import org.quartz.impl.jdbcjobstore.MSSQLDelegate;

/**
 * A custom {@link org.springframework.scheduling.quartz.SchedulerFactoryBean} that uses {@link DriverDelegateWrapper}.
 * Also necessary because Quartz's {@link org.quartz.impl.jdbcjobstore.JobStoreSupport} does a <a href=
 * "https://github.com/quartz-scheduler/quartz/blob/quartz-2.3.0/quartz-core/src/main/java/org/quartz/impl/jdbcjobstore/JobStoreSupport.java#L665">hard
 * coded check</a> on the name of the driver delegate class to determine the SQL lock statement.
 */
public class SchedulerFactoryBean extends org.springframework.scheduling.quartz.SchedulerFactoryBean {

	private static final String DRIVER_DELEGATE_CLASS = "org.quartz.jobStore.driverDelegateClass";
	private static final String DRIVER_DELEGATE_INIT_STRING = "org.quartz.jobStore.driverDelegateInitString";
	private static final String SELECT_WITH_LOCK_SQL = "org.quartz.jobStore.selectWithLockSQL";
	private static final String MSSSQL_LOCK_SQL = "SELECT * FROM {0}LOCKS WITH (UPDLOCK,ROWLOCK) WHERE SCHED_NAME = {1} AND LOCK_NAME = ?";
	private final String quartzDriverDelegate;

	public SchedulerFactoryBean(String quartzDriverDelegate) {
		this.quartzDriverDelegate = quartzDriverDelegate;
	}

	@Override
	public void setQuartzProperties(Properties quartzProperties) {
		quartzProperties.put(DRIVER_DELEGATE_CLASS, DriverDelegateWrapper.class.getName());
		quartzProperties.put(DRIVER_DELEGATE_INIT_STRING, "delegate=" + quartzDriverDelegate);
		if (!quartzProperties.containsKey(SELECT_WITH_LOCK_SQL)
				&& MSSQLDelegate.class.getName().equals(quartzDriverDelegate)) {
			quartzProperties.put(SELECT_WITH_LOCK_SQL, MSSSQL_LOCK_SQL);
		}

		super.setQuartzProperties(quartzProperties);
	}

}
