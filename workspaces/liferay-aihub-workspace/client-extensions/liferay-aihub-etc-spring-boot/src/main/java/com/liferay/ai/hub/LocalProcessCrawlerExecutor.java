/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub;

import com.liferay.petra.string.StringUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Map;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * @author José Abelenda
 */
@ConditionalOnProperty(
	havingValue = "local-process", matchIfMissing = true,
	name = "liferay.ai.hub.crawler.executor"
)
@Service
public class LocalProcessCrawlerExecutor implements CrawlerExecutor {

	@Override
	public String execute(CrawlerExecutorInput crawlerExecutorInput) {
		Path path = null;

		try {
			path = Files.createTempFile("crawler-config", ".yml");

			String crawlerConfig = null;

			try (InputStream inputStream = getClass().getResourceAsStream(
					"/crawler-config-template.yml")) {

				crawlerConfig = new String(
					inputStream.readAllBytes(), StandardCharsets.UTF_8);
			}

			crawlerConfig = _replace(
				Map.of(
					"[$CRAWLER_DOMAIN_URL$]",
					crawlerExecutorInput.getDomainUrl(),
					"[$CRAWLER_ELASTICSEARCH_HOST$]", _crawlerElasticsearchHost,
					"[$CRAWLER_ELASTICSEARCH_PIPELINE$]",
					_crawlerElasticsearchPipeline,
					"[$CRAWLER_ELASTICSEARCH_PORT$]",
					String.valueOf(_crawlerElasticsearchPort),
					"[$CRAWLER_MAX_CRAWL_DEPTH$]",
					String.valueOf(_crawlerMaxCrawlDepth),
					"[$CRAWLER_MAX_DURATION$]",
					String.valueOf(_crawlerMaxDuration),
					"[$CRAWLER_OUTPUT_INDEX$]",
					crawlerExecutorInput.getIndexName(), "[$CRAWLER_SEED_URL$]",
					crawlerExecutorInput.getSeedUrl(),
					"[$CRAWLER_URL_QUEUE_SIZE_LIMIT$]",
					String.valueOf(_crawlerUrlQueueSizeLimit)),
				crawlerConfig);

			Files.writeString(path, crawlerConfig, StandardCharsets.UTF_8);

			ProcessBuilder processBuilder = new ProcessBuilder(
				"bundle", "exec", "bin/crawler", "crawl",
				path.toAbsolutePath(
				).toString());

			processBuilder.directory(new File("/opt/liferay/crawler"));

			processBuilder.redirectErrorStream(true);

			if (_log.isInfoEnabled()) {
				_log.info(
					"Launching crawler: " +
						String.join(" ", processBuilder.command()));
			}

			Process process = processBuilder.start();

			try (BufferedReader bufferedReader = new BufferedReader(
					new InputStreamReader(
						process.getInputStream(), StandardCharsets.UTF_8))) {

				String line;

				while ((line = bufferedReader.readLine()) != null) {
					if (_log.isInfoEnabled()) {
						_log.info("[crawler] " + line);
					}
				}
			}

			int exitCode = process.waitFor();

			if (_log.isInfoEnabled()) {
				_log.info("Crawler finished with exit code " + exitCode);
			}

			if (exitCode != 0) {
				throw new RuntimeException(
					"Crawler finished with exit code " + exitCode);
			}

			return "local:" + UUID.randomUUID();
		}
		catch (InterruptedException interruptedException) {
			Thread.currentThread(
			).interrupt();

			throw new RuntimeException(
				"Crawler execution failed", interruptedException);
		}
		catch (RuntimeException runtimeException) {
			throw runtimeException;
		}
		catch (Exception exception) {
			throw new RuntimeException("Crawler execution failed", exception);
		}
		finally {
			if (path != null) {
				try {
					Files.deleteIfExists(path);
				}
				catch (Exception exception) {
					_log.error(
						"Unable to delete temporary crawler config", exception);
				}
			}
		}
	}

	private String _replace(Map<String, String> map, String string) {
		for (Map.Entry<String, String> entry : map.entrySet()) {
			string = StringUtil.replace(
				string, entry.getKey(), entry.getValue());
		}

		return string;
	}

	private static final Log _log = LogFactory.getLog(
		LocalProcessCrawlerExecutor.class);

	@Value("${liferay.ai.hub.crawler.elasticsearch.host}")
	private String _crawlerElasticsearchHost;

	@Value("${liferay.ai.hub.crawler.elasticsearch.pipeline}")
	private String _crawlerElasticsearchPipeline;

	@Value("${liferay.ai.hub.crawler.elasticsearch.port}")
	private int _crawlerElasticsearchPort;

	@Value("${liferay.ai.hub.crawler.max.crawl.depth}")
	private int _crawlerMaxCrawlDepth;

	@Value("${liferay.ai.hub.crawler.max.duration}")
	private int _crawlerMaxDuration;

	@Value("${liferay.ai.hub.crawler.url.queue.size.limit}")
	private int _crawlerUrlQueueSizeLimit;

}