/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Date;

/**
 * @author José Abelenda
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CrawlerJobDto {

	public String getCrawlerJobStatus() {
		return _crawlerJobStatus;
	}

	public Date getEndDate() {
		return _endDate;
	}

	public String getErrorMessage() {
		return _errorMessage;
	}

	public String getExecutionId() {
		return _executionId;
	}

	public String getExternalReferenceCode() {
		return _externalReferenceCode;
	}

	public Integer getIndexedDocumentCount() {
		return _indexedDocumentCount;
	}

	public Date getStartDate() {
		return _startDate;
	}

	public void setCrawlerJobStatus(String crawlerJobStatus) {
		_crawlerJobStatus = crawlerJobStatus;
	}

	public void setEndDate(Date endDate) {
		_endDate = endDate;
	}

	public void setErrorMessage(String errorMessage) {
		_errorMessage = errorMessage;
	}

	public void setExecutionId(String executionId) {
		_executionId = executionId;
	}

	public void setExternalReferenceCode(String externalReferenceCode) {
		_externalReferenceCode = externalReferenceCode;
	}

	public void setIndexedDocumentCount(Integer indexedDocumentCount) {
		_indexedDocumentCount = indexedDocumentCount;
	}

	public void setStartDate(Date startDate) {
		_startDate = startDate;
	}

	private String _crawlerJobStatus;
	private Date _endDate;
	private String _errorMessage;
	private String _executionId;
	private String _externalReferenceCode;
	private Integer _indexedDocumentCount;
	private Date _startDate;

}