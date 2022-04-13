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
package org.appng.application.scheduler.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.appng.api.SiteProperties;
import org.appng.api.model.Application;
import org.appng.api.model.Resource;
import org.appng.api.model.ResourceType;
import org.appng.api.model.Site;
import org.appng.core.controller.HttpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@link RestController}delivering Swagger-UI stuff
 * 
 * @author Matthias Müller
 */
@RestController
public class SwaggerUIController {

	private @Value("${swaggerBasicAuth:}") String basicAuth;

	@GetMapping(path = "/swagger-ui/{path:.+}", produces = { MediaType.TEXT_HTML_VALUE, "text/css",
			"application/javascript", MediaType.TEXT_PLAIN_VALUE })
	public ResponseEntity<byte[]> swaggerUI(Site site, Application application,
			@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@PathVariable(required = true) String path) throws IOException, URISyntaxException {

		MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
		if (StringUtils.isNotBlank(basicAuth)) {
			headers.add(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"appNG Scheduler OpenAPI\"");
			String expectedAuth = "Basic " + Base64.getEncoder().encodeToString(basicAuth.getBytes());
			if (!StringUtils.equals(authorization, expectedAuth)) {
				return new ResponseEntity<>(headers, HttpStatus.UNAUTHORIZED);
			}
		}

		byte[] data = null;
		if (path.endsWith(".yaml")) {
			try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
				if (null == is) {
					return ResponseEntity.notFound().build();
				}
				String spec = new String(IOUtils.toByteArray(is), StandardCharsets.UTF_8);
				String servicePath = site.getProperties().getString(SiteProperties.SERVICE_PATH);
				String fullPath = String.format("%s/%s/%s/rest/jobState", servicePath, site.getName(),
						application.getName());
				data = spec.replace("/jobState", fullPath).getBytes();
			}

		} else {
			Resource resource = application.getResources().getResource(ResourceType.RESOURCE, "swagger-ui/" + path);
			if (null == resource) {
				return ResponseEntity.notFound().build();
			}
			String mediaType = path.endsWith(".html") ? MediaType.TEXT_HTML_VALUE
					: (path.endsWith(".js") ? "application/javascript" : "text/css");
			headers.add(HttpHeaders.CONTENT_TYPE, mediaType);
			data = resource.getBytes();
		}
		return new ResponseEntity<byte[]>(data, headers, HttpStatus.OK);
	}
}
