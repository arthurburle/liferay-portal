#!/bin/sh

set -o errexit
set -o nounset

PREV_MANIFEST=/temp/.overlay-manifest

function main {
	if [ "${#}" -ne 2 ]
	then
		_log_json "Usage: ${0} <provider-type> <manifest-file>." "ERROR"

		exit 1
	fi

	local backend_options="${LIFERAY_OVERLAY_BACKEND_OPTIONS:-}"
	local bucket_name="${LIFERAY_OVERLAY_BUCKET_NAME:-}"
	local provider_type="${1}"
	local manifest="${2}"

	if [ -z "${bucket_name}" ]
	then
		_log_json "Overlay bucket does not exist (checked LIFERAY_OVERLAY_BUCKET_NAME). Skipping sync." "ERROR"

		exit 1
	fi

	_prune_orphans "${manifest}"

	local from_path into_path source_uri target_path

	while IFS="	" read -r from_path into_path
	do
		[ -z "${from_path}" ] && continue

		source_uri=":${provider_type},env_auth=true${backend_options:+,${backend_options}}:${bucket_name}/${from_path}"
		target_path="/temp/${into_path}"

		_log_json "Copying from \"${source_uri}\" to \"${target_path}\"."

		if [ "${from_path%/}" != "${from_path}" ]
		then
			rm -rf "${target_path}"
			mkdir -p "${target_path}"
			rclone copy "${source_uri}" "${target_path}" --inplace --log-level INFO --use-json-log
		else
			rclone copyto "${source_uri}" "${target_path}" --inplace --log-level INFO --use-json-log
		fi
	done < "${manifest}"

	awk -F"	" '{print $2}' "${manifest}" > "${PREV_MANIFEST}"

	_log_json "Sync completed successfully."
}

function _prune_orphans {
	local manifest="${1}"

	if [ ! -f "${PREV_MANIFEST}" ]
	then
		return 0
	fi

	local current_sorted prev_sorted orphan target

	current_sorted=$(mktemp)
	prev_sorted=$(mktemp)

	awk -F"	" '{print $2}' "${manifest}" | sort -u > "${current_sorted}"
	sort -u "${PREV_MANIFEST}" > "${prev_sorted}"

	comm -23 "${prev_sorted}" "${current_sorted}" | while IFS= read -r orphan
	do
		[ -z "${orphan}" ] && continue

		target="/temp/${orphan}"

		if [ -e "${target}" ]
		then
			rm -rf "${target}"
			_log_json "Removed orphan \"${target}\"."
		fi
	done

	rm -f "${current_sorted}" "${prev_sorted}"
}

function _log_json {
	local escaped_message

	escaped_message=$(echo "${1}" | sed 's/"/\\"/g')

	local script_name

	script_name=$(basename "${0}")

	local severity="${2:-INFO}"

	local timestamp

	timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

	printf '{"message": "%s", "script": "%s", "severity": "%s", "timestamp": "%s"}\n' "${escaped_message}" "${script_name}" "${severity}" "${timestamp}"
}

main "${@}"