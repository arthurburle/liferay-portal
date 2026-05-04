/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub;

import java.time.Instant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * @author José Abelenda
 */
@Service
public class CrawlJobClient {

	public CrawlJobClient(
		LiferayHeadlessTokenProvider liferayHeadlessTokenProvider,
		@Value("${liferay.ai.hub.crawler.headless.url}") String baseURL,
		@Value("${liferay.ai.hub.crawler.headless.crawljobs.path}") String
			crawlJobsPath) {

		_liferayHeadlessTokenProvider = liferayHeadlessTokenProvider;
		_baseURL = baseURL;
		_crawlJobsPath = crawlJobsPath;
	}

	public CrawlJobDto create(CrawlJobDto crawlJobDto) {
		return _restClient.post(
		).uri(
			_baseURL + _crawlJobsPath
		).header(
			HttpHeaders.AUTHORIZATION, _bearer()
		).contentType(
			MediaType.APPLICATION_JSON
		).body(
			crawlJobDto
		).retrieve(
		).body(
			CrawlJobDto.class
		);
	}

	public CrawlJobDto findActiveByDataSource(
		String dataSourceExternalReferenceCode) {

		String filter = String.format(
			"triggerObjectExternalReferenceCode eq '%s' and (crawlStatus eq " +
				"'QUEUED' or crawlStatus eq 'DISPATCHED' or crawlStatus eq " +
					"'RUNNING')",
			dataSourceExternalReferenceCode);

		Page page = _restClient.get(
		).uri(
			_baseURL + _crawlJobsPath + "?filter={filter}&pageSize=1", filter
		).header(
			HttpHeaders.AUTHORIZATION, _bearer()
		).retrieve(
		).body(
			Page.class
		);

		if ((page == null) || page.items.isEmpty()) {
			return null;
		}

		return page.items.get(0);
	}

	public List<CrawlJobDto> findRunningOlderThan(Instant cutoff) {
		String filter = String.format(
			"(crawlStatus eq 'DISPATCHED' or crawlStatus eq 'RUNNING') and " +
				"dateModified lt %s",
			cutoff.toString());

		Page page = _restClient.get(
		).uri(
			_baseURL + _crawlJobsPath + "?filter={filter}&pageSize=100", filter
		).header(
			HttpHeaders.AUTHORIZATION, _bearer()
		).retrieve(
		).body(
			Page.class
		);

		if (page == null) {
			return new ArrayList<>();
		}

		return page.items;
	}

	public CrawlJobDto updateByExternalReferenceCode(
		String externalReferenceCode, Map<String, Object> fields) {

		return _restClient.patch(
		).uri(
			_baseURL + _crawlJobsPath +
				"/by-external-reference-code/{externalReferenceCode}",
			externalReferenceCode
		).header(
			HttpHeaders.AUTHORIZATION, _bearer()
		).contentType(
			MediaType.APPLICATION_JSON
		).body(
			fields
		).retrieve(
		).body(
			CrawlJobDto.class
		);
	}

	private String _bearer() {
		return "Bearer " + _liferayHeadlessTokenProvider.token();
	}

	private final String _baseURL;
	private final String _crawlJobsPath;
	private final LiferayHeadlessTokenProvider _liferayHeadlessTokenProvider;
	private final RestClient _restClient = RestClient.create();

	private static final class Page {

		public List<CrawlJobDto> items = new ArrayList<>();

	}

}