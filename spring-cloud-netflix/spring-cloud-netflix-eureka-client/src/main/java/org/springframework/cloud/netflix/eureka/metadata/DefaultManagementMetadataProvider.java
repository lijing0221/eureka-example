/*
 * Copyright 2017-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.netflix.eureka.metadata;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.netflix.eureka.EurekaInstanceConfigBean;
import org.springframework.util.StringUtils;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Default implementation of {@link DefaultManagementMetadataProvider}.
 *
 * @author Anastasiia Smirnova
 */
public class DefaultManagementMetadataProvider implements ManagementMetadataProvider {

	private static final int RANDOM_PORT = 0;

	private static final Log log = LogFactory.getLog(DefaultManagementMetadataProvider.class);

	@Override
	public ManagementMetadata get(EurekaInstanceConfigBean instance, int serverPort, String serverContextPath,
			String managementContextPath, Integer managementPort) {
		if (isRandom(managementPort)) {
			return null;
		}
		if (managementPort == null && isRandom(serverPort)) {
			return null;
		}
		String healthCheckUrl = getHealthCheckUrl(instance, serverPort, serverContextPath, managementContextPath,
				managementPort, false);
		String statusPageUrl = getStatusPageUrl(instance, serverPort, serverContextPath, managementContextPath,
				managementPort);

		ManagementMetadata metadata = new ManagementMetadata(healthCheckUrl, statusPageUrl,
				managementPort == null ? serverPort : managementPort);
		if (instance.isSecurePortEnabled()) {
			metadata.setSecureHealthCheckUrl(getHealthCheckUrl(instance, serverPort, serverContextPath,
					managementContextPath, managementPort, true));
		}
		return metadata;
	}

	private boolean isRandom(Integer port) {
		return port != null && port == RANDOM_PORT;
	}

	protected String getHealthCheckUrl(EurekaInstanceConfigBean instance, int serverPort, String serverContextPath,
			String managementContextPath, Integer managementPort, boolean isSecure) {
		String healthCheckUrlPath = instance.getHealthCheckUrlPath();
		String healthCheckUrl = getUrl(instance, serverPort, serverContextPath, managementContextPath, managementPort,
				healthCheckUrlPath, isSecure);
		log.debug("Constructed eureka meta-data healthcheckUrl: " + healthCheckUrl);
		return healthCheckUrl;
	}

	public String getStatusPageUrl(EurekaInstanceConfigBean instance, int serverPort, String serverContextPath,
			String managementContextPath, Integer managementPort) {
		String statusPageUrlPath = instance.getStatusPageUrlPath();
		String statusPageUrl = getUrl(instance, serverPort, serverContextPath, managementContextPath, managementPort,
				statusPageUrlPath, false);
		log.debug("Constructed eureka meta-data statusPageUrl: " + statusPageUrl);
		return statusPageUrl;
	}

	private String getUrl(EurekaInstanceConfigBean instance, int serverPort, String serverContextPath,
			String managementContextPath, Integer managementPort, String urlPath, boolean isSecure) {
		managementContextPath = refineManagementContextPath(serverContextPath, managementContextPath, managementPort);
		if (managementPort == null) {
			managementPort = serverPort;
		}
		String scheme = isSecure ? "https" : "http";
		return constructValidUrl(scheme, instance.getHostname(), managementPort, managementContextPath, urlPath);
	}

	private String refineManagementContextPath(String serverContextPath, String managementContextPath,
			Integer managementPort) {
		// management context path is relative to server context path when no management
		// port is set
		if (managementContextPath != null && managementPort == null) {
			return serverContextPath + managementContextPath;
		}
		if (managementContextPath != null) {
			return managementContextPath;
		}
		if (managementPort != null) {
			return "/";
		}
		return serverContextPath;
	}

	private String constructValidUrl(String scheme, String hostname, int port, String contextPath, String statusPath) {
		try {
			if (!contextPath.endsWith("/")) {
				contextPath = contextPath + "/";
			}
			String refinedContextPath = '/' + StringUtils.trimLeadingCharacter(contextPath, '/');
			URL base = new URL(scheme, hostname, port, refinedContextPath);
			String refinedStatusPath = refinedStatusPath(statusPath, contextPath);
			return new URL(base, refinedStatusPath).toString();
		}
		catch (MalformedURLException e) {
			String message = getErrorMessage(scheme, hostname, port, contextPath, statusPath);
			throw new IllegalStateException(message, e);
		}
	}

	private String refinedStatusPath(String statusPath, String contextPath) {
		if (statusPath.startsWith(contextPath) && !"/".equals(contextPath)) {
			statusPath = StringUtils.replace(statusPath, contextPath, "");
		}
		return StringUtils.trimLeadingCharacter(statusPath, '/');
	}

	private String getErrorMessage(String scheme, String hostname, int port, String contextPath, String statusPath) {
		return String.format(
				"Failed to construct url for scheme: %s, hostName: %s port: %s contextPath: %s statusPath: %s", scheme,
				hostname, port, contextPath, statusPath);
	}

}
