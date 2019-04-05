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

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.quartz.Calendar;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.JobPersistenceException;
import org.quartz.TriggerKey;
import org.quartz.impl.jdbcjobstore.DriverDelegate;
import org.quartz.impl.jdbcjobstore.FiredTriggerRecord;
import org.quartz.impl.jdbcjobstore.NoSuchDelegateException;
import org.quartz.impl.jdbcjobstore.SchedulerStateRecord;
import org.quartz.impl.jdbcjobstore.TriggerStatus;
import org.quartz.impl.matchers.GroupMatcher;
import org.quartz.spi.ClassLoadHelper;
import org.quartz.spi.OperableTrigger;
import org.quartz.utils.Key;
import org.slf4j.Logger;

/**
 * A custom {@link DriverDelegate} that uses {@link SchedulerJobDetail}. This is a workaround for
 * <a href="https://github.com/quartz-scheduler/quartz/issues/184">Quartz issue #184</a>.
 * 
 * @author Matthias Müller
 *
 */
public class DriverDelegateWrapper implements DriverDelegate {

	DriverDelegate delegate;

	public void initialize(Logger logger, String tablePrefix, String schedName, String instanceId,
			ClassLoadHelper classLoadHelper, boolean useProperties, String initString) throws NoSuchDelegateException {
		if (StringUtils.isBlank(initString) || !StringUtils.contains(initString, "delegate=")) {
			throw new NoSuchDelegateException("initString must contain delegate=<delegate-class-name>");
		}
		String[] realDelegate = initString.split("\\|")[0].split("=");
		String delegateClass = realDelegate[1];
		try {
			this.delegate = classLoadHelper.loadClass(delegateClass, DriverDelegate.class).newInstance();
			this.delegate.initialize(logger, tablePrefix, schedName, instanceId, classLoadHelper, useProperties, null);
		} catch (ReflectiveOperationException e) {
			throw new NoSuchDelegateException(delegateClass, e);
		}
	}

	public SchedulerJobDetail selectJobDetail(Connection conn, JobKey jobKey, ClassLoadHelper loadHelper)
			throws ClassNotFoundException, IOException, SQLException {
		JobDetail jobDetail = delegate.selectJobDetail(conn, jobKey, loadHelper);
		return null != jobDetail ? new SchedulerJobDetail(jobDetail) : null;
	}

	// delegate methods
	public int updateTriggerStatesFromOtherStates(Connection conn, String newState, String oldState1, String oldState2)
			throws SQLException {
		return delegate.updateTriggerStatesFromOtherStates(conn, newState, oldState1, oldState2);
	}

	public List<TriggerKey> selectMisfiredTriggers(Connection conn, long ts) throws SQLException {
		return delegate.selectMisfiredTriggers(conn, ts);
	}

	public List<TriggerKey> selectMisfiredTriggersInState(Connection conn, String state, long ts) throws SQLException {
		return delegate.selectMisfiredTriggersInState(conn, state, ts);
	}

	public boolean hasMisfiredTriggersInState(Connection conn, String state1, long ts, int count,
			List<TriggerKey> resultList) throws SQLException {
		return delegate.hasMisfiredTriggersInState(conn, state1, ts, count, resultList);
	}

	public int countMisfiredTriggersInState(Connection conn, String state1, long ts) throws SQLException {
		return delegate.countMisfiredTriggersInState(conn, state1, ts);
	}

	public List<TriggerKey> selectMisfiredTriggersInGroupInState(Connection conn, String groupName, String state,
			long ts) throws SQLException {
		return delegate.selectMisfiredTriggersInGroupInState(conn, groupName, state, ts);
	}

	public List<OperableTrigger> selectTriggersForRecoveringJobs(Connection conn)
			throws SQLException, IOException, ClassNotFoundException {
		return delegate.selectTriggersForRecoveringJobs(conn);
	}

	public int deleteFiredTriggers(Connection conn) throws SQLException {
		return delegate.deleteFiredTriggers(conn);
	}

	public int deleteFiredTriggers(Connection conn, String instanceId) throws SQLException {
		return delegate.deleteFiredTriggers(conn, instanceId);
	}

	public int insertJobDetail(Connection conn, JobDetail job) throws IOException, SQLException {
		return delegate.insertJobDetail(conn, job);
	}

	public int updateJobDetail(Connection conn, JobDetail job) throws IOException, SQLException {
		return delegate.updateJobDetail(conn, job);
	}

	public List<TriggerKey> selectTriggerKeysForJob(Connection conn, JobKey jobKey) throws SQLException {
		return delegate.selectTriggerKeysForJob(conn, jobKey);
	}

	public int deleteJobDetail(Connection conn, JobKey jobKey) throws SQLException {
		return delegate.deleteJobDetail(conn, jobKey);
	}

	public boolean isJobNonConcurrent(Connection conn, JobKey jobKey) throws SQLException {
		return delegate.isJobNonConcurrent(conn, jobKey);
	}

	public boolean jobExists(Connection conn, JobKey jobKey) throws SQLException {
		return delegate.jobExists(conn, jobKey);
	}

	public int updateJobData(Connection conn, JobDetail job) throws IOException, SQLException {
		return delegate.updateJobData(conn, job);
	}

	public int selectNumJobs(Connection conn) throws SQLException {
		return delegate.selectNumJobs(conn);
	}

	public List<String> selectJobGroups(Connection conn) throws SQLException {
		return delegate.selectJobGroups(conn);
	}

	public Set<JobKey> selectJobsInGroup(Connection conn, GroupMatcher<JobKey> matcher) throws SQLException {
		return delegate.selectJobsInGroup(conn, matcher);
	}

	public int insertTrigger(Connection conn, OperableTrigger trigger, String state, JobDetail jobDetail)
			throws SQLException, IOException {
		return delegate.insertTrigger(conn, trigger, state, jobDetail);
	}

	public int updateTrigger(Connection conn, OperableTrigger trigger, String state, JobDetail jobDetail)
			throws SQLException, IOException {
		return delegate.updateTrigger(conn, trigger, state, jobDetail);
	}

	public boolean triggerExists(Connection conn, TriggerKey triggerKey) throws SQLException {
		return delegate.triggerExists(conn, triggerKey);
	}

	public int updateTriggerState(Connection conn, TriggerKey triggerKey, String state) throws SQLException {
		return delegate.updateTriggerState(conn, triggerKey, state);
	}

	public int updateTriggerStateFromOtherState(Connection conn, TriggerKey triggerKey, String newState,
			String oldState) throws SQLException {
		return delegate.updateTriggerStateFromOtherState(conn, triggerKey, newState, oldState);
	}

	public int updateTriggerStateFromOtherStates(Connection conn, TriggerKey triggerKey, String newState,
			String oldState1, String oldState2, String oldState3) throws SQLException {
		return delegate.updateTriggerStateFromOtherStates(conn, triggerKey, newState, oldState1, oldState2, oldState3);
	}

	public int updateTriggerGroupStateFromOtherStates(Connection conn, GroupMatcher<TriggerKey> matcher,
			String newState, String oldState1, String oldState2, String oldState3) throws SQLException {
		return delegate.updateTriggerGroupStateFromOtherStates(conn, matcher, newState, oldState1, oldState2,
				oldState3);
	}

	public int updateTriggerGroupStateFromOtherState(Connection conn, GroupMatcher<TriggerKey> matcher, String newState,
			String oldState) throws SQLException {
		return delegate.updateTriggerGroupStateFromOtherState(conn, matcher, newState, oldState);
	}

	public int updateTriggerStatesForJob(Connection conn, JobKey jobKey, String state) throws SQLException {
		return delegate.updateTriggerStatesForJob(conn, jobKey, state);
	}

	public int updateTriggerStatesForJobFromOtherState(Connection conn, JobKey jobKey, String state, String oldState)
			throws SQLException {
		return delegate.updateTriggerStatesForJobFromOtherState(conn, jobKey, state, oldState);
	}

	public int deleteTrigger(Connection conn, TriggerKey triggerKey) throws SQLException {
		return delegate.deleteTrigger(conn, triggerKey);
	}

	public int selectNumTriggersForJob(Connection conn, JobKey jobKey) throws SQLException {
		return delegate.selectNumTriggersForJob(conn, jobKey);
	}

	public JobDetail selectJobForTrigger(Connection conn, ClassLoadHelper loadHelper, TriggerKey triggerKey)
			throws ClassNotFoundException, SQLException {
		return delegate.selectJobForTrigger(conn, loadHelper, triggerKey);
	}

	public JobDetail selectJobForTrigger(Connection conn, ClassLoadHelper loadHelper, TriggerKey triggerKey,
			boolean loadJobClass) throws ClassNotFoundException, SQLException {
		return delegate.selectJobForTrigger(conn, loadHelper, triggerKey, loadJobClass);
	}

	public List<OperableTrigger> selectTriggersForJob(Connection conn, JobKey jobKey)
			throws SQLException, ClassNotFoundException, IOException, JobPersistenceException {
		return delegate.selectTriggersForJob(conn, jobKey);
	}

	public List<OperableTrigger> selectTriggersForCalendar(Connection conn, String calName)
			throws SQLException, ClassNotFoundException, IOException, JobPersistenceException {
		return delegate.selectTriggersForCalendar(conn, calName);
	}

	public OperableTrigger selectTrigger(Connection conn, TriggerKey triggerKey)
			throws SQLException, ClassNotFoundException, IOException, JobPersistenceException {
		return delegate.selectTrigger(conn, triggerKey);
	}

	public JobDataMap selectTriggerJobDataMap(Connection conn, String triggerName, String groupName)
			throws SQLException, ClassNotFoundException, IOException {
		return delegate.selectTriggerJobDataMap(conn, triggerName, groupName);
	}

	public String selectTriggerState(Connection conn, TriggerKey triggerKey) throws SQLException {
		return delegate.selectTriggerState(conn, triggerKey);
	}

	public TriggerStatus selectTriggerStatus(Connection conn, TriggerKey triggerKey) throws SQLException {
		return delegate.selectTriggerStatus(conn, triggerKey);
	}

	public int selectNumTriggers(Connection conn) throws SQLException {
		return delegate.selectNumTriggers(conn);
	}

	public List<String> selectTriggerGroups(Connection conn) throws SQLException {
		return delegate.selectTriggerGroups(conn);
	}

	public List<String> selectTriggerGroups(Connection conn, GroupMatcher<TriggerKey> matcher) throws SQLException {
		return delegate.selectTriggerGroups(conn, matcher);
	}

	public Set<TriggerKey> selectTriggersInGroup(Connection conn, GroupMatcher<TriggerKey> matcher)
			throws SQLException {
		return delegate.selectTriggersInGroup(conn, matcher);
	}

	public List<TriggerKey> selectTriggersInState(Connection conn, String state) throws SQLException {
		return delegate.selectTriggersInState(conn, state);
	}

	public int insertPausedTriggerGroup(Connection conn, String groupName) throws SQLException {
		return delegate.insertPausedTriggerGroup(conn, groupName);
	}

	public int deletePausedTriggerGroup(Connection conn, String groupName) throws SQLException {
		return delegate.deletePausedTriggerGroup(conn, groupName);
	}

	public int deletePausedTriggerGroup(Connection conn, GroupMatcher<TriggerKey> matcher) throws SQLException {
		return delegate.deletePausedTriggerGroup(conn, matcher);
	}

	public int deleteAllPausedTriggerGroups(Connection conn) throws SQLException {
		return delegate.deleteAllPausedTriggerGroups(conn);
	}

	public boolean isTriggerGroupPaused(Connection conn, String groupName) throws SQLException {
		return delegate.isTriggerGroupPaused(conn, groupName);
	}

	public Set<String> selectPausedTriggerGroups(Connection conn) throws SQLException {
		return delegate.selectPausedTriggerGroups(conn);
	}

	public boolean isExistingTriggerGroup(Connection conn, String groupName) throws SQLException {
		return delegate.isExistingTriggerGroup(conn, groupName);
	}

	public int insertCalendar(Connection conn, String calendarName, Calendar calendar)
			throws IOException, SQLException {
		return delegate.insertCalendar(conn, calendarName, calendar);
	}

	public int updateCalendar(Connection conn, String calendarName, Calendar calendar)
			throws IOException, SQLException {
		return delegate.updateCalendar(conn, calendarName, calendar);
	}

	public boolean calendarExists(Connection conn, String calendarName) throws SQLException {
		return delegate.calendarExists(conn, calendarName);
	}

	public Calendar selectCalendar(Connection conn, String calendarName)
			throws ClassNotFoundException, IOException, SQLException {
		return delegate.selectCalendar(conn, calendarName);
	}

	public boolean calendarIsReferenced(Connection conn, String calendarName) throws SQLException {
		return delegate.calendarIsReferenced(conn, calendarName);
	}

	public int deleteCalendar(Connection conn, String calendarName) throws SQLException {
		return delegate.deleteCalendar(conn, calendarName);
	}

	public int selectNumCalendars(Connection conn) throws SQLException {
		return delegate.selectNumCalendars(conn);
	}

	public List<String> selectCalendars(Connection conn) throws SQLException {
		return delegate.selectCalendars(conn);
	}

	public long selectNextFireTime(Connection conn) throws SQLException {
		return delegate.selectNextFireTime(conn);
	}

	public Key<?> selectTriggerForFireTime(Connection conn, long fireTime) throws SQLException {
		return delegate.selectTriggerForFireTime(conn, fireTime);
	}

	public List<TriggerKey> selectTriggerToAcquire(Connection conn, long noLaterThan, long noEarlierThan)
			throws SQLException {
		return delegate.selectTriggerToAcquire(conn, noLaterThan, noEarlierThan);
	}

	public List<TriggerKey> selectTriggerToAcquire(Connection conn, long noLaterThan, long noEarlierThan, int maxCount)
			throws SQLException {
		return delegate.selectTriggerToAcquire(conn, noLaterThan, noEarlierThan, maxCount);
	}

	public int insertFiredTrigger(Connection conn, OperableTrigger trigger, String state, JobDetail jobDetail)
			throws SQLException {
		return delegate.insertFiredTrigger(conn, trigger, state, jobDetail);
	}

	public int updateFiredTrigger(Connection conn, OperableTrigger trigger, String state, JobDetail jobDetail)
			throws SQLException {
		return delegate.updateFiredTrigger(conn, trigger, state, jobDetail);
	}

	public List<FiredTriggerRecord> selectFiredTriggerRecords(Connection conn, String triggerName, String groupName)
			throws SQLException {
		return delegate.selectFiredTriggerRecords(conn, triggerName, groupName);
	}

	public List<FiredTriggerRecord> selectFiredTriggerRecordsByJob(Connection conn, String jobName, String groupName)
			throws SQLException {
		return delegate.selectFiredTriggerRecordsByJob(conn, jobName, groupName);
	}

	public List<FiredTriggerRecord> selectInstancesFiredTriggerRecords(Connection conn, String instanceName)
			throws SQLException {
		return delegate.selectInstancesFiredTriggerRecords(conn, instanceName);
	}

	public Set<String> selectFiredTriggerInstanceNames(Connection conn) throws SQLException {
		return delegate.selectFiredTriggerInstanceNames(conn);
	}

	public int deleteFiredTrigger(Connection conn, String entryId) throws SQLException {
		return delegate.deleteFiredTrigger(conn, entryId);
	}

	public int selectJobExecutionCount(Connection conn, JobKey jobKey) throws SQLException {
		return delegate.selectJobExecutionCount(conn, jobKey);
	}

	public int insertSchedulerState(Connection conn, String instanceId, long checkInTime, long interval)
			throws SQLException {
		return delegate.insertSchedulerState(conn, instanceId, checkInTime, interval);
	}

	public int deleteSchedulerState(Connection conn, String instanceId) throws SQLException {
		return delegate.deleteSchedulerState(conn, instanceId);
	}

	public int updateSchedulerState(Connection conn, String instanceId, long checkInTime) throws SQLException {
		return delegate.updateSchedulerState(conn, instanceId, checkInTime);
	}

	public List<SchedulerStateRecord> selectSchedulerStateRecords(Connection conn, String instanceId)
			throws SQLException {
		return delegate.selectSchedulerStateRecords(conn, instanceId);
	}

	public void clearData(Connection conn) throws SQLException {
		delegate.clearData(conn);
	}

}
