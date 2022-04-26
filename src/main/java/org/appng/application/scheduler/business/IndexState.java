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
import org.appng.api.ScheduledJob;
import org.appng.api.Scope;
import org.appng.api.SiteProperties;
import org.appng.api.model.Application;
import org.appng.api.model.Site;
import org.appng.api.model.Site.SiteState;
import org.appng.application.scheduler.Constants;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class IndexState implements DataProvider {

	@Override
	public DataContainer getData(Site site, Application application, Environment env, Options options, Request request,
			FieldProcessor fp) {
		Map<String, Site> siteMap = env.getAttribute(Scope.PLATFORM, Platform.Environment.SITES);
		List<IndexInfo> data = new ArrayList<IndexInfo>();

		new TreeSet<>(siteMap.keySet()).forEach(siteName -> {
			Site theSite = siteMap.get(siteName);

			log.warn("########## {}", theSite);

			Application schedulerApp = theSite.getApplication(application.getName());
			if (null != schedulerApp) {

				log.warn("########## {}", schedulerApp);

				IndexInfo indexInfo = new IndexInfo(siteName);
				indexInfo.siteRunning = theSite.hasState(SiteState.STARTED);

				try {
					ScheduledJob indexJob = schedulerApp.getBean("indexJob", ScheduledJob.class);
					if (null != indexJob) {
						Map<String, Object> indexJobData = indexJob.getJobDataMap();
						indexInfo.indexEnabled = (Boolean) indexJobData.get(Constants.JOB_ENABLED);
						indexInfo.cronExpression = (String) indexJobData.get(Constants.JOB_CRON_EXPRESSION);

						indexInfo.link = String.format(
								"/manager/%s/appng-scheduler/jobs/update/appng-scheduler_indexJob#sections_jobs=tab_update",
								siteName);
					}
				} catch (Exception e) {
					log.error("error reading index for site " + siteName, e);
				}

				String siteRoot = theSite.getProperties().getString(SiteProperties.SITE_ROOT_DIR);
				String indexdir = theSite.getProperties().getString(SiteProperties.INDEX_DIR);
				File indexPath = new File(siteRoot, indexdir);
				indexInfo.location = indexPath.getAbsolutePath();

				if (indexPath.exists()) {
					try (FSDirectory dir = FSDirectory.open(indexPath.toPath(), NoLockFactory.INSTANCE);
							DirectoryReader reader = DirectoryReader.open(dir);) {
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
