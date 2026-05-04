/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * @author José Abelenda
 */
@Service
public class LiferayHeadlessTokenProvider {

	public LiferayHeadlessTokenProvider(
		@Value("${liferay.ai.hub.crawler.headless.url}") String baseURL,
		@Value("${liferay.ai.hub.crawler.headless.client.id}") String clientId,
		@Value("${liferay.ai.hub.crawler.headless.client.secret}") String
			clientSecret) {

		_baseURL = baseURL;
		_clientId = clientId;
		_clientSecret = clientSecret;
	}

	public synchronized String token() {
		long now = System.currentTimeMillis();

		if ((_token != null) && (now < (_expiresAtMillis - 30_000))) {
			return _token;
		}

		MultiValueMap<String, String> body = new LinkedMultiValueMap<>();

		body.add("client_id", _clientId);
		body.add("client_secret", _clientSecret);
		body.add("grant_type", "client_credentials");

		Map<?, ?> response = _restClient.post(
		).uri(
			_baseURL + "/o/oauth2/token"
		).contentType(
			MediaType.APPLICATION_FORM_URLENCODED
		).body(
			body
		).retrieve(
		).body(
			Map.class
		);

		_token = (String)response.get("access_token");

		Number expiresIn = (Number)response.get("expires_in");

		_expiresAtMillis = now + (expiresIn.longValue() * 1000);

		return _token;
	}

	private final String _baseURL;
	private final String _clientId;
	private final String _clientSecret;
	private volatile long _expiresAtMillis;
	private final RestClient _restClient = RestClient.create();
	private volatile String _token;

}