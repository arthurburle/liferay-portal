/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub;

import com.google.cloud.run.v2.Execution;
import com.google.cloud.run.v2.ExecutionsClient;
import com.google.protobuf.Timestamp;

import com.liferay.petra.string.StringBundler;

import java.time.Duration;
import java.time.Instant;

import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * @author José Abelenda
 */
@ConditionalOnProperty(
	havingValue = "cloud-run-jobs", name = "liferay.ai.hub.crawler.executor"
)
@Service
public class CrawlerJobReaper {

	public CrawlerJobReaper(
		CrawlerJobClient crawlerJobClient, ExecutionsClient executionsClient,
		@Value("${liferay.ai.hub.crawler.reaper.stale.minutes}") int
			staleMinutes) {

		_crawlerJobClient = crawlerJobClient;
		_executionsClient = executionsClient;
		_staleMinutes = staleMinutes;
	}

	@Scheduled(cron = "${liferay.ai.hub.crawler.reaper.cron}")
	public void reap() {
		Instant cutoff = Instant.now(
		).minus(
			Duration.ofMinutes(_staleMinutes)
		);

		List<CrawlerJobDto> staleCrawlerJobDtos =
			_crawlerJobClient.findRunningOlderThan(cutoff);

		if (_log.isInfoEnabled()) {
			_log.info(
				StringBundler.concat(
					"Reaper found ", staleCrawlerJobDtos.size(),
					" stale CrawlerJobs older than ", cutoff));
		}

		for (CrawlerJobDto crawlerJobDto : staleCrawlerJobDtos) {
			try {
				_reconcile(crawlerJobDto);
			}
			catch (Exception exception) {
				_log.error(
					"Failed to reconcile CrawlerJob " +
						crawlerJobDto.getExternalReferenceCode(),
					exception);
			}
		}
	}

	private void _reconcile(CrawlerJobDto crawlerJobDto) {
		String executionId = crawlerJobDto.getExecutionId();

		if ((executionId == null) || executionId.startsWith("local:")) {
			_crawlerJobClient.updateByExternalReferenceCode(
				crawlerJobDto.getExternalReferenceCode(),
				Map.of(
					"crawlerJobStatus", "abandoned", "errorMessage",
					"Execution not reconcilable; reaped"));

			return;
		}

		Execution execution = _executionsClient.getExecution(executionId);

		if (!execution.hasCompletionTime()) {
			return;
		}

		String crawlerJobStatus = "failed";

		if ((execution.getSucceededCount() > 0) &&
			(execution.getFailedCount() == 0)) {

			crawlerJobStatus = "succeeded";
		}

		Timestamp completionTime = execution.getCompletionTime();

		Instant endDate = Instant.ofEpochSecond(
			completionTime.getSeconds(), completionTime.getNanos());

		if (crawlerJobStatus.equals("succeeded")) {
			_crawlerJobClient.updateByExternalReferenceCode(
				crawlerJobDto.getExternalReferenceCode(),
				Map.of(
					"crawlerJobStatus", crawlerJobStatus, "endDate",
					endDate.toString()));

			return;
		}

		_crawlerJobClient.updateByExternalReferenceCode(
			crawlerJobDto.getExternalReferenceCode(),
			Map.of(
				"crawlerJobStatus", crawlerJobStatus, "endDate",
				endDate.toString(), "errorMessage",
				"Reaped from Cloud Run execution"));
	}

	private static final Log _log = LogFactory.getLog(CrawlerJobReaper.class);

	private final CrawlerJobClient _crawlerJobClient;
	private final ExecutionsClient _executionsClient;
	private final int _staleMinutes;

}