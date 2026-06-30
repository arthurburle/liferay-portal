/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub;

import com.liferay.client.extension.util.spring.boot3.BaseRestController;

import java.net.URI;

import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.json.JSONObject;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author José Abelenda
 */
@RequestMapping("/object/action/crawler")
@RestController
public class ObjectActionCrawlerRestController extends BaseRestController {

	public ObjectActionCrawlerRestController(
		CrawlerExecutor crawlerExecutor, CrawlerJobClient crawlerJobClient) {

		_crawlerExecutor = crawlerExecutor;
		_crawlerJobClient = crawlerJobClient;

		if (_log.isInfoEnabled()) {
			String executorClassName = crawlerExecutor.getClass(
			).getSimpleName();

			_log.info("Active crawler executor: " + executorClassName);
		}
	}

	@PostMapping
	public ResponseEntity<Map<String, String>> post(
		@AuthenticationPrincipal Jwt jwt, @RequestBody String json) {

		if (_log.isDebugEnabled()) {
			_log.debug(json);
		}

		JSONObject jsonObject = new JSONObject(json);

		JSONObject objectEntryJSONObject = jsonObject.getJSONObject(
			"objectEntry");

		JSONObject valuesJSONObject = objectEntryJSONObject.getJSONObject(
			"values");

		long accountEntryId = valuesJSONObject.getLong(
			"r_accountToAIHubContentRetrievers_accountEntryId");
		String indexName = valuesJSONObject.getString("indexName");
		String seedUrl = valuesJSONObject.getString("url");

		long aiHubContentRetrieverId = objectEntryJSONObject.getLong("id");

		URI seedURI = URI.create(seedUrl);

		String domainUrl = seedURI.getScheme() + "://" + seedURI.getAuthority();

		try {
			CrawlerJobDto activeCrawlerJobDto =
				_crawlerJobClient.findActiveByContentRetriever(
					aiHubContentRetrieverId);

			if (activeCrawlerJobDto != null) {
				return ResponseEntity.accepted(
				).body(
					Map.of(
						"deduped", "true", "externalReferenceCode",
						activeCrawlerJobDto.getExternalReferenceCode())
				);
			}

			CrawlerJobDto createdCrawlerJobDto = _crawlerJobClient.create(
				Map.of(
					"crawlerJobStatus", "queued",
					"r_accountToAIHubCrawlerJobs_accountEntryId",
					accountEntryId,
					"r_contentRetrieverToCrawlerJobs_aiHubContentRetrieverId",
					aiHubContentRetrieverId));

			String executionId = _crawlerExecutor.execute(
				new CrawlerExecutorInput(domainUrl, indexName, seedUrl));

			_crawlerJobClient.updateByExternalReferenceCode(
				createdCrawlerJobDto.getExternalReferenceCode(),
				Map.of(
					"crawlerJobStatus", "dispatched", "executionId",
					executionId));

			return ResponseEntity.accepted(
			).body(
				Map.of(
					"executionId", executionId, "externalReferenceCode",
					createdCrawlerJobDto.getExternalReferenceCode())
			);
		}
		catch (Exception exception) {
			_log.error("Crawler dispatch failed", exception);

			return new ResponseEntity<>(
				Map.of("error", String.valueOf(exception.getMessage())),
				HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	private static final Log _log = LogFactory.getLog(
		ObjectActionCrawlerRestController.class);

	private final CrawlerExecutor _crawlerExecutor;
	private final CrawlerJobClient _crawlerJobClient;

}