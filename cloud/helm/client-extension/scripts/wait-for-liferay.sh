#!/bin/sh

set -o errexit
set -o nounset

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

function _log_json {
	local escaped_message

	escaped_message=$(printf '%s' "${1}" | sed 's/\\/\\\\/g; s/"/\\"/g')

	local script_name

	script_name=$(basename "${0}")

	local severity="${2:-INFO}"

	local timestamp

	timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

	printf '{"message": "%s", "script": "%s", "severity": "%s", "timestamp": "%s"}\n' "${escaped_message}" "${script_name}" "${severity}" "${timestamp}"
}

main