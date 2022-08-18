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
package org.appng.application.scheduler.quartz;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import javax.sql.DataSource;

import org.quartz.impl.jdbcjobstore.MSSQLDelegate;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import lombok.extern.slf4j.Slf4j;

/**
 * A custom {@link org.springframework.scheduling.quartz.SchedulerFactoryBean} that uses {@link DriverDelegateWrapper}.
 * Also necessary because Quartz's {@link org.quartz.impl.jdbcjobstore.JobStoreSupport} does a <a href=
 * "https://github.com/quartz-scheduler/quartz/blob/quartz-2.3.0/quartz-core/src/main/java/org/quartz/impl/jdbcjobstore/JobStoreSupport.java#L665">hard
 * coded check</a> on the name of the driver delegate class to determine the SQL lock statement.
 */
@Slf4j
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
		if (isMsSql() && !quartzProperties.containsKey(SELECT_WITH_LOCK_SQL)) {
			quartzProperties.put(SELECT_WITH_LOCK_SQL, MSSSQL_LOCK_SQL);
		}
		log.info("Quartz properties: {}", quartzProperties);
		super.setQuartzProperties(quartzProperties);
	}

	@Override
	public void setDataSource(DataSource original) {
		if (isMsSql()) {
			// workaround for APPNG-2448
			HikariConfig config = new HikariConfig();
			HikariDataSource.class.cast(original).copyStateTo(config);
			config.setAutoCommit(true);
			config.setPoolName("appNG Quartz Hikari Pool");

			String jdbcUrl = config.getJdbcUrl();
			if (null != jdbcUrl) {
				config.setJdbcUrl(getModifiedJdbcUrl(jdbcUrl));
			} else {
				jdbcUrl = config.getDataSourceProperties().getProperty("URL");
				config.addDataSourceProperty("URL", getModifiedJdbcUrl(jdbcUrl));
			}

			HikariDataSource dataSource = new HikariDataSource(config);
			try (Connection con = dataSource.getConnection()) {
				log.info("Datasource: {} (autocommit: {})", dataSource, con.getAutoCommit());
			} catch (SQLException e) {
				log.error("Error testing connection", e);
			}
			super.setDataSource(dataSource);
		} else {
			super.setDataSource(original);
		}
	}

	private String getModifiedJdbcUrl(String jdbcUrl) {
		String modified = jdbcUrl + ";applicationName=appNG_Quartz_Scheduling";
		log.info("Modified JDBC URL: {}", modified);
		return modified;
	}

	private boolean isMsSql() {
		return MSSQLDelegate.class.getName().equals(quartzDriverDelegate);
	}

}
