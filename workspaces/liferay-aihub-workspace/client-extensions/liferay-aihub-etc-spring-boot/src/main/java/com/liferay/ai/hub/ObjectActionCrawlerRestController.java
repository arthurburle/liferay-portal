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
		CrawlerExecutor crawlerExecutor, CrawlJobClient crawlJobClient) {

		_crawlerExecutor = crawlerExecutor;
		_crawlJobClient = crawlJobClient;

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

		String triggerObjectExternalReferenceCode =
			objectEntryJSONObject.getString("externalReferenceCode");

		String indexName = valuesJSONObject.getString("indexName");

		String seedUrl = valuesJSONObject.getString("url");

		URI seedURI = URI.create(seedUrl);

		String domainUrl = seedURI.getScheme() + "://" + seedURI.getAuthority();

		try {
			CrawlJobDto crawlJobDto = new CrawlJobDto();

			crawlJobDto.setCrawlStatus("QUEUED");
			crawlJobDto.setDomainUrl(domainUrl);
			crawlJobDto.setIndexName(indexName);
			crawlJobDto.setSeedUrl(seedUrl);
			crawlJobDto.setTriggerObjectExternalReferenceCode(
				triggerObjectExternalReferenceCode);

			CrawlJobDto createdCrawlJobDto = _crawlJobClient.create(
				crawlJobDto);

			String executionName = _crawlerExecutor.execute(
				new CrawlerExecutorInput(domainUrl, indexName, seedUrl));

			_crawlJobClient.updateByExternalReferenceCode(
				createdCrawlJobDto.getExternalReferenceCode(),
				Map.of(
					"crawlStatus", "DISPATCHED", "executionName",
					executionName));

			return ResponseEntity.accepted(
			).body(
				Map.of(
					"executionName", executionName, "externalReferenceCode",
					createdCrawlJobDto.getExternalReferenceCode())
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
	private final CrawlJobClient _crawlJobClient;

}