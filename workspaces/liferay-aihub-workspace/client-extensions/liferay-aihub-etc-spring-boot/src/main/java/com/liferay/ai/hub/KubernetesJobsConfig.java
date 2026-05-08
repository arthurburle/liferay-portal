/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author José Abelenda
 */
@ConditionalOnProperty(
	havingValue = "kubernetes-jobs", name = "liferay.ai.hub.crawler.executor"
)
@Configuration
public class KubernetesJobsConfig {

	@Bean(destroyMethod = "close")
	public KubernetesClient kubernetesClient() {
		return new KubernetesClientBuilder(
		).build();
	}

}