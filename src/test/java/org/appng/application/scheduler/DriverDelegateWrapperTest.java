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
package org.appng.application.scheduler;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.appng.application.scheduler.quartz.DriverDelegateWrapper;
import org.appng.application.scheduler.quartz.SchedulerJobDetail;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.SchedulerConfigException;
import org.quartz.impl.jdbcjobstore.HSQLDBDelegate;
import org.quartz.impl.jdbcjobstore.InvalidConfigurationException;
import org.quartz.impl.jdbcjobstore.JobStoreSupport;
import org.quartz.impl.jdbcjobstore.JobStoreTX;
import org.quartz.simpl.SimpleClassLoadHelper;
import org.quartz.spi.SchedulerSignaler;
import org.quartz.utils.ConnectionProvider;
import org.quartz.utils.DBConnectionManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseFactoryBean;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * Test for {@link DriverDelegateWrapper}
 * 
 * @author Matthias Müller
 *
 */
public class DriverDelegateWrapperTest {

	@Test
	public void testJobRetrieval() throws Exception {
		JobDetail job = getJobStore().retrieveJob(new JobKey("name", "group"));
		Assert.assertNull(job);
	}

	@Test
	public void testConcurrentExectionDisallowed() throws Exception {

		JobStoreSupport jobStore = getJobStore();

		JobDetail job1 = JobBuilder.newJob(Job.class).withIdentity(new JobKey("nonConcurrent", "group")).build();
		SchedulerJobDetail nonConcurrentJob = new SchedulerJobDetail(job1);
		Assert.assertTrue(nonConcurrentJob.isConcurrentExectionDisallowed());
		jobStore.storeJob(nonConcurrentJob, true);

		JobDetail retrieveNonConcurrentJob = jobStore.retrieveJob(nonConcurrentJob.getKey());
		Assert.assertEquals(nonConcurrentJob.isConcurrentExectionDisallowed(),
				retrieveNonConcurrentJob.isConcurrentExectionDisallowed());

		JobDetail job2 = JobBuilder.newJob(Job.class).withIdentity(new JobKey("concurrentJob", "group")).build();
		job2.getJobDataMap().put(Constants.JOB_ALLOW_CONCURRENT_EXECUTIONS, true);
		SchedulerJobDetail concurrentJob = new SchedulerJobDetail(job2);
		Assert.assertFalse(concurrentJob.isConcurrentExectionDisallowed());
		jobStore.storeJob(concurrentJob, true);

		JobDetail retrieveConcurrentJob = jobStore.retrieveJob(job2.getKey());
		Assert.assertEquals(job2.isConcurrentExectionDisallowed(),
				retrieveConcurrentJob.isConcurrentExectionDisallowed());

	}

	private JobStoreSupport getJobStore() throws InvalidConfigurationException, SchedulerConfigException {
		EmbeddedDatabaseFactoryBean edbfb = new EmbeddedDatabaseFactoryBean();
		edbfb.setDatabasePopulator(new ResourceDatabasePopulator(new ClassPathResource("sql/init_quartz_tables.sql")));
		edbfb.afterPropertiesSet();
		final DataSource datasource = edbfb.getObject();

		DBConnectionManager.getInstance().addConnectionProvider("datasource", new ConnectionProvider() {

			public void shutdown() throws SQLException {
			}

			public void initialize() throws SQLException {
			}

			public Connection getConnection() throws SQLException {
				return datasource.getConnection();
			}
		});
		JobStoreSupport jobStore = new JobStoreTX();
		jobStore.setDriverDelegateClass(DriverDelegateWrapper.class.getName());
		jobStore.setDriverDelegateInitString("delegate=" + HSQLDBDelegate.class.getName());
		jobStore.setDataSource("datasource");
		jobStore.initialize(new SimpleClassLoadHelper(), Mockito.mock(SchedulerSignaler.class));
		return jobStore;
	}

}
