package org.appng.application.scheduler.configuration;

import java.io.IOException;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Properties;

import javax.sql.DataSource;

import org.appng.api.Platform;
import org.appng.api.ScheduledJob;
import org.appng.application.scheduler.Constants;
import org.appng.application.scheduler.job.IndexJob;
import org.appng.application.scheduler.job.JobRecordHouseKeepingJob;
import org.appng.application.scheduler.quartz.DriverDelegateWrapper;
import org.appng.application.scheduler.quartz.SpringQuartzSchedulerFactory;
import org.appng.application.scheduler.service.JobRecordService;
import org.appng.scheduler.openapi.model.JobState.TimeunitEnum;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.impl.jdbcjobstore.HSQLDBDelegate;
import org.quartz.impl.jdbcjobstore.MSSQLDelegate;
import org.quartz.impl.jdbcjobstore.PostgreSQLDelegate;
import org.quartz.impl.jdbcjobstore.StdJDBCDelegate;
import org.quartz.simpl.HostnameInstanceIdGenerator;
import org.quartz.spi.JobFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

@Configuration
public class SchedulerConfig {

	private static final String SCHEDULER_PREFIX = "Scheduler_";

	private static final String JOBSTORE_PREFIX = "org.quartz.jobStore.";
	private static final String CLUSTER_CHECKIN_INTERVAL = JOBSTORE_PREFIX + "clusterCheckinInterval";
	private static final String DRIVER_DELEGATE_CLASS = JOBSTORE_PREFIX + "driverDelegateClass";
	private static final String DRIVER_DELEGATE_INIT_STRING = JOBSTORE_PREFIX + "driverDelegateInitString";
	private static final String IS_CLUSTERED = JOBSTORE_PREFIX + "isClustered";
	private static final String SELECT_WITH_LOCK_SQL = JOBSTORE_PREFIX + "selectWithLockSQL";

	private static final String MSSQL_LOCK_SQL = "SELECT * FROM {0}LOCKS WITH (UPDLOCK,ROWLOCK) WHERE SCHED_NAME = {1} AND LOCK_NAME = ?";
	private static final String MYSQL_LOCK_SQL = "SELECT * FROM {0}LOCKS WHERE SCHED_NAME = {1} AND LOCK_NAME = ? LOCK IN SHARE MODE;";

	@Bean
	public DataSourceTransactionManager quartzTransactionManager(DataSource dataSource) {
		return new DataSourceTransactionManager(dataSource);
	}

	@Bean
	public SchedulerFactoryBean scheduler(JobFactory jobFactory,
			@Value("${quartzDriverDelegate}") String quartzDriverDelegate, @Value("${site.name}") String siteName,
			DataSourceTransactionManager quartzTransactionManager, Properties quartzProperties) throws SQLException {
		DataSource dataSource = quartzTransactionManager.getDataSource();
		SchedulerFactoryBean scheduler = new SchedulerFactoryBean();

		scheduler.setSchedulerName(SCHEDULER_PREFIX + siteName);
		scheduler.setAutoStartup(false);
		scheduler.setOverwriteExistingJobs(true);
		scheduler.setDataSource(dataSource);
		scheduler.setTransactionManager(quartzTransactionManager);
		scheduler.setJobFactory(jobFactory);

		String driverDelegate = StdJDBCDelegate.class.getName();
		String lockSql = null;
		if (!quartzProperties.contains(SELECT_WITH_LOCK_SQL)) {
			try (Connection connection = dataSource.getConnection()) {
				String databaseProductName = connection.getMetaData().getDatabaseProductName().toLowerCase();
				if (databaseProductName.contains("mysql") || databaseProductName.contains("mariadb")) {
					lockSql = MYSQL_LOCK_SQL;
				} else if (databaseProductName.contains("mssql")) {
					lockSql = MSSQL_LOCK_SQL;
					driverDelegate = MSSQLDelegate.class.getName();
				} else if (databaseProductName.contains("postgres")) {
					driverDelegate = PostgreSQLDelegate.class.getName();
				} else if (databaseProductName.contains("hsql")) {
					// http://www.hsqldb.org/doc/2.0/guide/sessions-chapt.html#snc_tx_mvcc
					try (CallableStatement stmt = connection.prepareCall("SET DATABASE TRANSACTION CONTROL MVCC")) {
						stmt.execute();
					}
					driverDelegate = HSQLDBDelegate.class.getName();
				}
			}
		}
		if (null != lockSql) {
			quartzProperties.put(SELECT_WITH_LOCK_SQL, lockSql);
		}
		scheduler.setQuartzProperties(quartzProperties);

		quartzProperties.put(DRIVER_DELEGATE_INIT_STRING, "delegate=" + driverDelegate);
		return scheduler;
	}

	@Bean
	public JobFactory jobFactory() {
		return new SpringQuartzSchedulerFactory();
	}

	@Bean
	Properties quartzProperties(@Value("${site.name}") String siteName,
			@Value("${platform.messagingEnabled:false}") boolean clustered,
			@Value("${quartzClusterCheckinInterval:20000}") String clusterCheckinInterval) {
		Properties props = new Properties();
		props.put(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME, SCHEDULER_PREFIX + siteName);
		props.put(StdSchedulerFactory.PROP_SCHED_THREAD_NAME, SCHEDULER_PREFIX + siteName);
		props.put(StdSchedulerFactory.PROP_SCHED_INSTANCE_ID, "AUTO");
		props.put(StdSchedulerFactory.PROP_SCHED_INTERRUPT_JOBS_ON_SHUTDOWN, true);
		props.put(StdSchedulerFactory.PROP_SCHED_INSTANCE_ID_GENERATOR_CLASS,
				HostnameInstanceIdGenerator.class.getName());

		props.put("org.quartz.threadPool.threadCount", "3");
		props.put("org.quartz.scheduler.skipUpdateCheck", true);
		props.put(IS_CLUSTERED, clustered);
		props.put(CLUSTER_CHECKIN_INTERVAL, clusterCheckinInterval);
		props.put(DRIVER_DELEGATE_CLASS, DriverDelegateWrapper.class.getName());
		return props;
	}

	@Bean
	public ScheduledJob indexJob(@Value("${indexEnabled}") boolean indexEnabled,
			@Value("${indexExpression}") String indexExpression, @Value("${platform.jspFileType}") String jspFileType) {
		IndexJob indexJob = new IndexJob();
		indexJob.setJobDataMap(new HashMap<>());
		indexJob.setDescription("Indexing of JSPs and static content");
		indexJob.getJobDataMap().put(Constants.JOB_ENABLED, indexEnabled);
		indexJob.getJobDataMap().put(Constants.JOB_CRON_EXPRESSION, indexExpression);
		indexJob.getJobDataMap().put(Platform.Property.JSP_FILE_TYPE, jspFileType);
		indexJob.getJobDataMap().put(Constants.THRESHOLD_TIMEUNIT, TimeunitEnum.DAY.name());
		indexJob.getJobDataMap().put(Constants.THRESHOLD_ERROR, 1);
		return indexJob;
	}

	@Bean
	public ScheduledJob houseKeepingJob(JobRecordService jobRecordService,
			@Value("${houseKeepingEnabled}") boolean houseKeepingEnabled,
			@Value("${houseKeepingExpression}") String houseKeepingExpression) {
		JobRecordHouseKeepingJob houseKeepingJob = new JobRecordHouseKeepingJob(jobRecordService);
		houseKeepingJob.setJobDataMap(new HashMap<>());
		houseKeepingJob.getJobDataMap().put(Constants.JOB_ENABLED, houseKeepingEnabled);
		houseKeepingJob.getJobDataMap().put(Constants.JOB_CRON_EXPRESSION, houseKeepingExpression);
		houseKeepingJob.getJobDataMap().put(Constants.JOB_RUN_ONCE, true);
		return houseKeepingJob;
	}

	@Bean
	public ObjectMapper objectMapper() {
		Module dateModule = new SimpleModule().addSerializer(OffsetDateTime.class,
				new JsonSerializer<OffsetDateTime>() {
					public void serialize(OffsetDateTime value, JsonGenerator jsonGenerator,
							SerializerProvider provider) throws IOException {
						if (value != null) {
							jsonGenerator.writeString(DateTimeFormatter.ISO_DATE_TIME.format(value));
						}
					}
				});
		return new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
				.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).registerModule(dateModule);
	}

	@Bean
	public MappingJackson2HttpMessageConverter jsonConverter(ObjectMapper objectMapper) {
		return new MappingJackson2HttpMessageConverter(objectMapper);
	}

}
