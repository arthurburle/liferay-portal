/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub;

/**
 * @author José Abelenda
 */
public class CrawlerExecutorInput {

	public CrawlerExecutorInput(
		String domainUrl, String indexName, String seedUrl) {

		_domainUrl = domainUrl;
		_indexName = indexName;
		_seedUrl = seedUrl;
	}

	public String getDomainUrl() {
		return _domainUrl;
	}

	public String getIndexName() {
		return _indexName;
	}

	public String getSeedUrl() {
		return _seedUrl;
	}

	private final String _domainUrl;
	private final String _indexName;
	private final String _seedUrl;

}