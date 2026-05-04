/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Date;

/**
 * @author José Abelenda
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CrawlJobDto {

	public String getCrawlStatus() {
		return _crawlStatus;
	}

	public Integer getDocsIndexed() {
		return _docsIndexed;
	}

	public String getDomainUrl() {
		return _domainUrl;
	}

	public String getExecutionName() {
		return _executionName;
	}

	public String getExternalReferenceCode() {
		return _externalReferenceCode;
	}

	public Date getFinishedAt() {
		return _finishedAt;
	}

	public String getIndexName() {
		return _indexName;
	}

	public String getLastError() {
		return _lastError;
	}

	public String getLockedBy() {
		return _lockedBy;
	}

	public String getSeedUrl() {
		return _seedUrl;
	}

	public Date getStartedAt() {
		return _startedAt;
	}

	public String getTriggerObjectExternalReferenceCode() {
		return _triggerObjectExternalReferenceCode;
	}

	public void setCrawlStatus(String crawlStatus) {
		_crawlStatus = crawlStatus;
	}

	public void setDocsIndexed(Integer docsIndexed) {
		_docsIndexed = docsIndexed;
	}

	public void setDomainUrl(String domainUrl) {
		_domainUrl = domainUrl;
	}

	public void setExecutionName(String executionName) {
		_executionName = executionName;
	}

	public void setExternalReferenceCode(String externalReferenceCode) {
		_externalReferenceCode = externalReferenceCode;
	}

	public void setFinishedAt(Date finishedAt) {
		_finishedAt = finishedAt;
	}

	public void setIndexName(String indexName) {
		_indexName = indexName;
	}

	public void setLastError(String lastError) {
		_lastError = lastError;
	}

	public void setLockedBy(String lockedBy) {
		_lockedBy = lockedBy;
	}

	public void setSeedUrl(String seedUrl) {
		_seedUrl = seedUrl;
	}

	public void setStartedAt(Date startedAt) {
		_startedAt = startedAt;
	}

	public void setTriggerObjectExternalReferenceCode(
		String triggerObjectExternalReferenceCode) {

		_triggerObjectExternalReferenceCode =
			triggerObjectExternalReferenceCode;
	}

	private String _crawlStatus;
	private Integer _docsIndexed;
	private String _domainUrl;
	private String _executionName;
	private String _externalReferenceCode;
	private Date _finishedAt;
	private String _indexName;
	private String _lastError;
	private String _lockedBy;
	private String _seedUrl;
	private Date _startedAt;
	private String _triggerObjectExternalReferenceCode;

}