#!/bin/sh

set -o errexit
set -o nounset

. /liferay-init-scripts/helpers.sh

function main {
	if [ "${#}" -ne 3 ]
	then
		_log_json "Usage: ${0} <provider-type> <from-path> <into-path>." "ERROR"

		exit 1
	fi

	local bucket_name="${LIFERAY_OVERLAY_BUCKET_NAME:-}"
	local from_path="${2}"
	local into_path="${3}"
	local provider_type="${1}"

	if [ -z "${bucket_name}" ]
	then
		_log_json "Overlay bucket does not exist (checked LIFERAY_OVERLAY_BUCKET_NAME). Skipping sync." "ERROR"

		exit 1
	fi

	local include_pattern=""

	if echo "${from_path}" | grep -q "\*"
	then
		include_pattern="${from_path##*/}"

		from_path="${from_path%/*}"
	fi

	local source_uri=":${provider_type},env_auth=true:${bucket_name}/${from_path}"
	local target_path="/temp/${into_path}"

	_log_json "Copying from \"${source_uri}\" to \"${target_path}\"."

	if [ -n "${include_pattern}" ]
	then
		rclone copy "${source_uri}" "${target_path}" --include "${include_pattern}" --inplace --log-level INFO --use-json-log
	elif [ "${from_path%/}" != "${from_path}" ]
	then
		rm -rf "${target_path}"
		mkdir -p "${target_path}"
		rclone copy "${source_uri}" "${target_path}" --inplace --log-level INFO --use-json-log
	else
		rclone copyto "${source_uri}" "${target_path}" --inplace --log-level INFO --use-json-log
	fi

	_log_json "Copy completed successfully."
}

main "${@}"
