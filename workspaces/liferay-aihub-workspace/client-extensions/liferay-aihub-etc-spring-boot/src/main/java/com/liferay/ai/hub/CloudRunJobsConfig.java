/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub;

import com.google.cloud.run.v2.ExecutionsClient;
import com.google.cloud.run.v2.JobsClient;

import java.io.IOException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author José Abelenda
 */
@ConditionalOnProperty(
	havingValue = "cloud-run-jobs", name = "liferay.ai.hub.crawler.executor"
)
@Configuration
@EnableScheduling
public class CloudRunJobsConfig {

	@Bean(destroyMethod = "close")
	public ExecutionsClient executionsClient() throws IOException {
		return ExecutionsClient.create();
	}

	@Bean(destroyMethod = "close")
	public JobsClient jobsClient() throws IOException {
		return JobsClient.create();
	}

}