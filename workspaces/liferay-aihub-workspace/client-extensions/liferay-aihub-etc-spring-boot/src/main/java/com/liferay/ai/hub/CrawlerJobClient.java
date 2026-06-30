/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub;

import com.liferay.petra.string.StringBundler;

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
public class CrawlerJobClient {

	public CrawlerJobClient(
		LiferayHeadlessTokenProvider liferayHeadlessTokenProvider,
		@Value("${liferay.ai.hub.crawler.headless.url}") String baseURL,
		@Value("${liferay.ai.hub.crawler.headless.crawlerjobs.path}") String
			crawlerJobsPath) {

		_liferayHeadlessTokenProvider = liferayHeadlessTokenProvider;
		_baseURL = baseURL;
		_crawlerJobsPath = crawlerJobsPath;
	}

	public CrawlerJobDto create(Map<String, Object> fields) {
		return _restClient.post(
		).uri(
			_baseURL + _crawlerJobsPath
		).header(
			HttpHeaders.AUTHORIZATION, _bearer()
		).contentType(
			MediaType.APPLICATION_JSON
		).body(
			fields
		).retrieve(
		).body(
			CrawlerJobDto.class
		);
	}

	public CrawlerJobDto findActiveByContentRetriever(
		long aiHubContentRetrieverId) {

		String filter = StringBundler.concat(
			"r_contentRetrieverToCrawlerJobs_aiHubContentRetrieverId eq ",
			aiHubContentRetrieverId,
			" and (crawlerJobStatus eq 'queued' or crawlerJobStatus eq ",
			"'dispatched' or crawlerJobStatus eq 'running')");

		Page page = _restClient.get(
		).uri(
			_baseURL + _crawlerJobsPath + "?filter={filter}&pageSize=1", filter
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

	public List<CrawlerJobDto> findRunningOlderThan(Instant cutoff) {
		String filter = String.format(
			"(crawlerJobStatus eq 'dispatched' or crawlerJobStatus eq " +
				"'running') and dateModified lt %s",
			cutoff.toString());

		Page page = _restClient.get(
		).uri(
			_baseURL + _crawlerJobsPath + "?filter={filter}&pageSize=100",
			filter
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

	public CrawlerJobDto updateByExternalReferenceCode(
		String externalReferenceCode, Map<String, Object> fields) {

		return _restClient.patch(
		).uri(
			_baseURL + _crawlerJobsPath +
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
			CrawlerJobDto.class
		);
	}

	private String _bearer() {
		return "Bearer " + _liferayHeadlessTokenProvider.token();
	}

	private final String _baseURL;
	private final String _crawlerJobsPath;
	private final LiferayHeadlessTokenProvider _liferayHeadlessTokenProvider;
	private final RestClient _restClient = RestClient.create();

	private static final class Page {

		public List<CrawlerJobDto> items = new ArrayList<>();

	}

}