/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub;

import com.google.api.gax.longrunning.OperationFuture;
import com.google.cloud.run.v2.EnvVar;
import com.google.cloud.run.v2.Execution;
import com.google.cloud.run.v2.JobName;
import com.google.cloud.run.v2.JobsClient;
import com.google.cloud.run.v2.RunJobRequest;

import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * @author José Abelenda
 */
@ConditionalOnProperty(
	havingValue = "cloud-run-jobs", name = "liferay.ai.hub.crawler.executor"
)
@Service
public class CloudRunJobsCrawlerExecutor implements CrawlerExecutor {

	public CloudRunJobsCrawlerExecutor(
		JobsClient jobsClient,
		@Value("${liferay.ai.hub.crawler.gcp.job.name}") String jobName,
		@Value("${liferay.ai.hub.crawler.gcp.project}") String project,
		@Value("${liferay.ai.hub.crawler.gcp.region}") String region) {

		_jobsClient = jobsClient;
		_jobName = jobName;
		_project = project;
		_region = region;
	}

	@Override
	public void execute(CrawlerExecutorInput crawlerExecutorInput) {
		String jobPath = JobName.of(
			_project, _region, _jobName
		).toString();

		RunJobRequest.Overrides.ContainerOverride.Builder
			containerOverrideBuilder =
				RunJobRequest.Overrides.ContainerOverride.newBuilder();

		containerOverrideBuilder.addEnv(
			_envVar("CRAWLER_DOMAIN_URL", crawlerExecutorInput.getDomainUrl()));
		containerOverrideBuilder.addEnv(
			_envVar(
				"CRAWLER_OUTPUT_INDEX", crawlerExecutorInput.getIndexName()));
		containerOverrideBuilder.addEnv(
			_envVar("CRAWLER_SEED_URL", crawlerExecutorInput.getSeedUrl()));

		RunJobRequest runJobRequest = RunJobRequest.newBuilder(
		).setName(
			jobPath
		).setOverrides(
			RunJobRequest.Overrides.newBuilder(
			).addContainerOverrides(
				containerOverrideBuilder.build()
			).build()
		).build();

		try {
			OperationFuture<Execution, Execution> operationFuture =
				_jobsClient.runJobAsync(runJobRequest);

			if (_log.isInfoEnabled()) {
				Execution execution = operationFuture.getMetadata(
				).get(
					30, TimeUnit.SECONDS
				);

				_log.info(
					"Cloud Run Job execution dispatched: " +
						execution.getName());
			}
		}
		catch (Exception exception) {
			throw new RuntimeException(
				"Unable to dispatch Cloud Run Job: " + exception.getMessage(),
				exception);
		}
	}

	private EnvVar _envVar(String name, String value) {
		return EnvVar.newBuilder(
		).setName(
			name
		).setValue(
			value
		).build();
	}

	private static final Log _log = LogFactory.getLog(
		CloudRunJobsCrawlerExecutor.class);

	private final String _jobName;
	private final JobsClient _jobsClient;
	private final String _project;
	private final String _region;

}