/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub;

import com.liferay.client.extension.util.spring.boot3.BaseRestController;

import java.net.URI;

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

	public ObjectActionCrawlerRestController(CrawlerExecutor crawlerExecutor) {
		_crawlerExecutor = crawlerExecutor;

		if (_log.isInfoEnabled()) {
			String executorClassName = crawlerExecutor.getClass(
			).getSimpleName();

			_log.info("Active crawler executor: " + executorClassName);
		}
	}

	@PostMapping
	public ResponseEntity<String> post(
		@AuthenticationPrincipal Jwt jwt, @RequestBody String json) {

		if (_log.isDebugEnabled()) {
			_log.debug(json);
		}

		JSONObject jsonObject = new JSONObject(json);

		JSONObject objectEntryJSONObject = jsonObject.getJSONObject(
			"objectEntry");

		JSONObject valuesJSONObject = objectEntryJSONObject.getJSONObject(
			"values");

		String seedUrl = valuesJSONObject.getString("url");

		URI seedURI = URI.create(seedUrl);

		String domainUrl = seedURI.getScheme() + "://" + seedURI.getAuthority();

		try {
			_crawlerExecutor.execute(
				new CrawlerExecutorInput(
					domainUrl, valuesJSONObject.getString("indexName"),
					seedUrl));

			return ResponseEntity.ok(
			).build();
		}
		catch (Exception exception) {
			_log.error("Crawler execution failed", exception);

			return new ResponseEntity<>(
				exception.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	private static final Log _log = LogFactory.getLog(
		ObjectActionCrawlerRestController.class);

	private final CrawlerExecutor _crawlerExecutor;

}