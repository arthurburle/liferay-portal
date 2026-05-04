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
public class CrawlJobReaper {

	public CrawlJobReaper(
		CrawlJobClient crawlJobClient, ExecutionsClient executionsClient,
		@Value("${liferay.ai.hub.crawler.reaper.stale.minutes}") int
			staleMinutes) {

		_crawlJobClient = crawlJobClient;
		_executionsClient = executionsClient;
		_staleMinutes = staleMinutes;
	}

	@Scheduled(cron = "${liferay.ai.hub.crawler.reaper.cron}")
	public void reap() {
		Instant cutoff = Instant.now(
		).minus(
			Duration.ofMinutes(_staleMinutes)
		);

		List<CrawlJobDto> staleCrawlJobDtos =
			_crawlJobClient.findRunningOlderThan(cutoff);

		if (_log.isInfoEnabled()) {
			_log.info(
				StringBundler.concat(
					"Reaper found ", staleCrawlJobDtos.size(),
					" stale CrawlJobs older than ", cutoff));
		}

		for (CrawlJobDto crawlJobDto : staleCrawlJobDtos) {
			try {
				_reconcile(crawlJobDto);
			}
			catch (Exception exception) {
				_log.error(
					"Failed to reconcile CrawlJob " +
						crawlJobDto.getExternalReferenceCode(),
					exception);
			}
		}
	}

	private void _reconcile(CrawlJobDto crawlJobDto) {
		String executionName = crawlJobDto.getExecutionName();

		if ((executionName == null) || executionName.startsWith("local:")) {
			_crawlJobClient.updateByExternalReferenceCode(
				crawlJobDto.getExternalReferenceCode(),
				Map.of(
					"crawlStatus", "ABANDONED", "lastError",
					"Execution not reconcilable; reaped"));

			return;
		}

		Execution execution = _executionsClient.getExecution(executionName);

		if (!execution.hasCompletionTime()) {
			return;
		}

		String crawlStatus = "FAILED";

		if ((execution.getSucceededCount() > 0) &&
			(execution.getFailedCount() == 0)) {

			crawlStatus = "SUCCEEDED";
		}

		Timestamp completionTime = execution.getCompletionTime();

		Instant finishedAt = Instant.ofEpochSecond(
			completionTime.getSeconds(), completionTime.getNanos());

		if (crawlStatus.equals("SUCCEEDED")) {
			_crawlJobClient.updateByExternalReferenceCode(
				crawlJobDto.getExternalReferenceCode(),
				Map.of(
					"crawlStatus", crawlStatus, "finishedAt",
					finishedAt.toString()));

			return;
		}

		_crawlJobClient.updateByExternalReferenceCode(
			crawlJobDto.getExternalReferenceCode(),
			Map.of(
				"crawlStatus", crawlStatus, "finishedAt", finishedAt.toString(),
				"lastError", "Reaped from Cloud Run execution"));
	}

	private static final Log _log = LogFactory.getLog(CrawlJobReaper.class);

	private final CrawlJobClient _crawlJobClient;
	private final ExecutionsClient _executionsClient;
	private final int _staleMinutes;

}