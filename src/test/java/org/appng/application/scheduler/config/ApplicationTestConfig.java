package org.appng.application.scheduler.config;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.appng.api.Platform;
import org.appng.api.Scope;
import org.appng.api.VHostMode;
import org.appng.api.model.Properties;
import org.appng.api.model.Property;
import org.appng.api.model.SimpleProperty;
import org.appng.api.support.PropertyHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.mock.web.MockServletContext;

@Configuration
// TODO remove when moved to appNG 1.25.x (see https://appng.org/jira/browse/APPNG-2427)
public class ApplicationTestConfig extends org.appng.testsupport.config.ApplicationTestConfig {

	@Bean
	@Primary
	@Override
	public MockServletContext servletContext() {
		MockServletContext ctx = new MockServletContext(new FileSystemResourceLoader());
		Map<String, Object> platformEnv = new ConcurrentHashMap<>();
		Property vhostMode = new SimpleProperty(Platform.Property.VHOST_MODE, VHostMode.NAME_BASED.name());
		Properties platformProps = new PropertyHolder("", Arrays.asList(vhostMode));
		platformEnv.put(Platform.Environment.PLATFORM_CONFIG, platformProps);
		ctx.setAttribute(Scope.PLATFORM.name(), platformEnv);
		return ctx;
	}
}
