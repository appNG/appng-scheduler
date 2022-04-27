/*
 * Copyright 2011-2022 the original author or authors.
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
package org.appng.application.scheduler.business;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.NoLockFactory;
import org.appng.api.DataContainer;
import org.appng.api.DataProvider;
import org.appng.api.Environment;
import org.appng.api.FieldProcessor;
import org.appng.api.Options;
import org.appng.api.Platform;
import org.appng.api.Request;
import org.appng.api.Scope;
import org.appng.api.SiteProperties;
import org.appng.api.model.Application;
import org.appng.api.model.Site;
import org.appng.api.model.Site.SiteState;
import org.appng.application.scheduler.Constants;
import org.appng.application.scheduler.SchedulerUtils;
import org.appng.application.scheduler.job.IndexJob;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * A {@link DataProvider} showing the state of the Lucene Index written by {@link IndexJob}.
 */
@Slf4j
@Component
public class IndexState extends SchedulerAware implements DataProvider {

	public IndexState(Scheduler scheduler) {
		super(scheduler);
	}

	@Override
	public DataContainer getData(Site site, Application application, Environment env, Options options, Request request,
			FieldProcessor fp) {
		Map<String, Site> siteMap = env.getAttribute(Scope.PLATFORM, Platform.Environment.SITES);
		List<IndexInfo> data = new ArrayList<IndexInfo>();

		new TreeSet<>(siteMap.keySet()).forEach(siteName -> {
			Site theSite = siteMap.get(siteName);

			Application schedulerApp = theSite.getApplication(application.getName());
			if (null != schedulerApp) {

				IndexInfo indexInfo = new IndexInfo(siteName);
				indexInfo.siteRunning = theSite.hasState(SiteState.STARTED);

				try {
					if (indexInfo.siteRunning) {

						Scheduler scheduler = schedulerApp.getBean(Scheduler.class);
						SchedulerUtils schedulerUtils = new SchedulerUtils(scheduler, fp, request);

						JobKey jobKey = SchedulerUtils.getJobKey(siteName, application.getName(), "indexJob");
						JobDetail jobDetail = schedulerUtils.getJobDetail(jobKey);

						Map<String, Object> indexJobData = jobDetail.getJobDataMap();
						indexInfo.indexEnabled = (Boolean) indexJobData.get(Constants.JOB_ENABLED);
						indexInfo.cronExpression = (String) indexJobData.get(Constants.JOB_CRON_EXPRESSION);

						indexInfo.link = String.format(
								"/manager/%s/appng-scheduler/jobs/update/appng-scheduler_indexJob#sections_jobs=tab_update",
								siteName);
					}
				} catch (SchedulerException e) {
					log.error("error reading index for site " + siteName, e);
				}

				String siteRoot = theSite.getProperties().getString(SiteProperties.SITE_ROOT_DIR);
				String indexdir = theSite.getProperties().getString(SiteProperties.INDEX_DIR);
				File indexPath = new File(siteRoot, indexdir);
				indexInfo.location = indexPath.getAbsolutePath();

				if (indexPath.exists()) {
					try (FSDirectory dir = FSDirectory.open(indexPath.toPath(), NoLockFactory.INSTANCE);
							DirectoryReader reader = DirectoryReader.open(dir)) {
						for (File file : indexPath.listFiles()) {
							indexInfo.files++;
							indexInfo.size += file.length();
							if (null == indexInfo.lastModified
									|| file.lastModified() > indexInfo.lastModified.getTime()) {
								indexInfo.lastModified = new Date(file.lastModified());
							}
						}
						indexInfo.numDocs = reader.numDocs();
					} catch (IOException e) {
						log.error("error reading index for site " + siteName, e);
					}
				}
				indexInfo.size %= 1000; // size in KB
				data.add(indexInfo);
			}

		});

		DataContainer dataContainer = new DataContainer(fp);
		dataContainer.setItems(data);
		return dataContainer;
	}

	@Getter
	@RequiredArgsConstructor
	public static class IndexInfo {
		final String site;
		String location;
		boolean siteRunning = false;
		boolean indexEnabled = false;
		String cronExpression;
		long numDocs = 0;
		long size = 0;
		int files = 0;
		Date lastModified;
		String link;
	}

}
