/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub;

import com.liferay.client.extension.util.spring.boot3.BaseRestController;

import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author José Abelenda
 */
@ConditionalOnProperty(
	havingValue = "true", name = "liferay.ai.hub.crawler.smoke.endpoint.enabled"
)
@RequestMapping("/smoke/dispatch")
@RestController
public class CrawlerSmokeDispatchRestController extends BaseRestController {

	public CrawlerSmokeDispatchRestController(CrawlerExecutor crawlerExecutor) {
		_crawlerExecutor = crawlerExecutor;

		if (_log.isWarnEnabled()) {
			_log.warn(
				"Smoke dispatch endpoint enabled — POC use only, not for " +
					"production");
		}
	}

	@PostMapping
	public ResponseEntity<Map<String, String>> post(
		@RequestBody Map<String, String> body) {

		String executionId = _crawlerExecutor.execute(
			new CrawlerExecutorInput(
				body.get("domainUrl"), body.get("indexName"),
				body.get("seedUrl")));

		return ResponseEntity.accepted(
		).body(
			Map.of("executionId", executionId)
		);
	}

	private static final Log _log = LogFactory.getLog(
		CrawlerSmokeDispatchRestController.class);

	private final CrawlerExecutor _crawlerExecutor;

}