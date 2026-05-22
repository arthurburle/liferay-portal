#!/bin/bash
set -uo pipefail
# See README.md for env vars, modes, exit codes, and report format.

: "${CRAWLER_DRY_RUN:=true}"
: "${CRAWLER_DOMAIN_URL:?missing CRAWLER_DOMAIN_URL}"
: "${CRAWLER_SEED_URL:?missing CRAWLER_SEED_URL}"
: "${CRAWLER_OUTPUT_INDEX:?missing CRAWLER_OUTPUT_INDEX}"

if [[ "${CRAWLER_DRY_RUN}" != "true" ]]; then
	: "${ELASTICSEARCH_HOST:?missing ELASTICSEARCH_HOST}"
	: "${ELASTICSEARCH_PORT:?missing ELASTICSEARCH_PORT}"
	: "${TENANT_AVAILABLE_QUOTA_TOKENS:?missing TENANT_AVAILABLE_QUOTA_TOKENS}"
fi
: "${TENANT_ID:=unknown}"

log() {
	echo "[$(date -Iseconds)] $*"
}

if [[ "${CRAWLER_DRY_RUN}" == "true" ]]; then
	log "DRY RUN: crawler writes to disk; wrapper not started"

	cat > /tmp/crawl.yml <<EOF
domains:
    -   url: "${CRAWLER_DOMAIN_URL}"
        seed_urls:
            -   "${CRAWLER_SEED_URL}"

output_sink: file
output_dir: /tmp/crawled_docs
EOF
else
	log "Starting quota wrapper (tenant=${TENANT_ID}, available_quota=${TENANT_AVAILABLE_QUOTA_TOKENS})"
	ELASTICSEARCH_HOST_REAL="${ELASTICSEARCH_HOST}" \
	ELASTICSEARCH_PORT_REAL="${ELASTICSEARCH_PORT}" \
	TENANT_AVAILABLE_QUOTA_TOKENS="${TENANT_AVAILABLE_QUOTA_TOKENS}" \
	TENANT_ID="${TENANT_ID}" \
	ruby /opt/liferay/quota_wrapper.rb &
	WRAPPER_PID=$!

	# Safety net: stop the wrapper if the script exits unexpectedly.
	trap 'kill -TERM ${WRAPPER_PID} 2>/dev/null; wait ${WRAPPER_PID} 2>/dev/null' EXIT

	for i in $(seq 1 30); do
		if (echo > /dev/tcp/127.0.0.1/9200) 2>/dev/null; then
			log "Wrapper ready"
			break
		fi
		if [ "$i" -eq 30 ]; then
			log "Wrapper failed to start within 15s"
			exit 1
		fi
		sleep 0.5
	done

	cat > /tmp/crawl.yml <<EOF
domains:
    -   url: "${CRAWLER_DOMAIN_URL}"
        seed_urls:
            -   "${CRAWLER_SEED_URL}"

output_index: "${CRAWLER_OUTPUT_INDEX}"
output_sink: elasticsearch

elasticsearch:
    bulk_api:
        max_items: 100
        max_size_bytes: 1048576
    host: "localhost"
    pipeline_enabled: false
    port: 9200
EOF
fi

log "Starting crawler with seed=${CRAWLER_SEED_URL} index=${CRAWLER_OUTPUT_INDEX} dry_run=${CRAWLER_DRY_RUN}"

# Tee so the wrapper can parse "Crawl Stats" / "Ingestion Stats" on shutdown.
bundle exec bin/crawler crawl /tmp/crawl.yml 2>&1 | tee /tmp/crawl.log
exit_code=${PIPESTATUS[0]}

log "Crawler finished with exit code ${exit_code}"

if [[ "${CRAWLER_DRY_RUN}" == "true" ]]; then
	log "Running post-process to estimate token consumption"
	TENANT_ID="${TENANT_ID}" \
	TENANT_AVAILABLE_QUOTA_TOKENS="${TENANT_AVAILABLE_QUOTA_TOKENS:-}" \
	ruby /opt/liferay/quota_wrapper.rb --post-process
	post_process_exit=$?

	# Only override on crawler success - never mask a real crawler failure.
	if [[ "${exit_code}" -eq 0 && "${post_process_exit}" -ne 0 ]]; then
		log "Post-process flagged would_exceed_quota; Job will fail"
		exit_code=${post_process_exit}
	fi
else
	# Signal the wrapper and wait so the report file is written before we read it.
	kill -TERM ${WRAPPER_PID} 2>/dev/null
	wait ${WRAPPER_PID} 2>/dev/null

	# The crawler's own exit code doesn't reflect bulk rejections; force exit 2
	# if any batch was rejected so the K8s Job is marked Failed.
	if [ -f /tmp/wrapper-report.json ]; then
		rejected=$(ruby -rjson -e 'puts JSON.parse(File.read("/tmp/wrapper-report.json")).dig("tokens","bulks_rejected").to_i' 2>/dev/null)
		if [ "${rejected:-0}" -gt 0 ]; then
			log "Wrapper rejected ${rejected} batches (quota exhausted); Job will fail (exit 2)"
			exit_code=2
		fi
	fi
fi

exit ${exit_code}
