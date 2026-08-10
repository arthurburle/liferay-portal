#!/bin/sh

set -o errexit
set -o nounset

. /liferay-init-scripts/helpers.sh

function main {
	local poll_interval="${POLL_INTERVAL_SECONDS:-5}"

	_log_json "Waiting for Liferay to be reachable at \"${LIFERAY_URL}\"."

	until wget -qO- "${LIFERAY_URL}" >/dev/null 2>&1
	do
		_log_json "Waiting for Liferay (unreachable)."

		sleep "${poll_interval}"
	done

	_log_json "Liferay is ready."
}

main
