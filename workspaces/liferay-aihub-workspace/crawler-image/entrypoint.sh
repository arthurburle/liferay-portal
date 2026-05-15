#!/bin/bash
set -uo pipefail

: "${CRAWLER_DOMAIN_URL:?missing CRAWLER_DOMAIN_URL}"
: "${CRAWLER_OUTPUT_INDEX:?missing CRAWLER_OUTPUT_INDEX}"
: "${CRAWLER_SEED_URL:?missing CRAWLER_SEED_URL}"
: "${ELASTICSEARCH_HOST:?missing ELASTICSEARCH_HOST}"
: "${ELASTICSEARCH_PORT:?missing ELASTICSEARCH_PORT}"

log() {
	echo "[$(date -Iseconds)] $*"
}

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
    host: "${ELASTICSEARCH_HOST}"
    pipeline_enabled: false
    port: ${ELASTICSEARCH_PORT}
EOF

log "Starting crawler with seed=${CRAWLER_SEED_URL} index=${CRAWLER_OUTPUT_INDEX}"

bundle exec bin/crawler crawl /tmp/crawl.yml
exit_code=$?

log "Crawler finished with exit code ${exit_code}"

exit ${exit_code}
