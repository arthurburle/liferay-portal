#!/bin/sh

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
