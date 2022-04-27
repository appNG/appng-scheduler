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
package org.appng.application.scheduler;

import java.util.Properties;

import org.appng.api.Platform;
import org.appng.api.SiteProperties;
import org.quartz.impl.jdbcjobstore.HSQLDBDelegate;

public class SchedulingProperties {

	public static Properties getProperties() {
		Properties properties = new Properties();
		properties.put("repositoryBase", "org.appng.core.repository");
		properties.put("entityPackage", "org.appng.core.domain");
		properties.put("indexExpression", "0 0/5 * * * ? 2042");
		properties.put("houseKeepingExpression", "0 0/5 * * * ? 2042");
		properties.put("indexEnabled", "false");
		properties.put("site.name", "localhost");
		properties.put("validateJobsOnStartup", "false");
		properties.put("houseKeepingEnabled", "false");
		properties.put("bearerToken", "TheBearer");
		properties.put("quartzDriverDelegate", HSQLDBDelegate.class.getName());
		properties.put("platform." + Platform.Property.JSP_FILE_TYPE, ".jsp");
		properties.put("site." + SiteProperties.SERVICE_PATH, "/service");
		properties.put("site.jsonPrettyPrint", "true");
		return properties;
	}

}
