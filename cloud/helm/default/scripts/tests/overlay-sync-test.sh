#!/usr/bin/env bash

set -o errexit
set -o nounset
set -o pipefail

function main {
	local fail=0
	local pass=0
	local script

	script=$(cd "$(dirname "${0}")/.." && pwd)/overlay-sync.sh

	export -f rclone

	_run_test "${script}" _test_exits_with_error_when_argument_count_is_wrong
	_run_test "${script}" _test_exits_with_error_when_no_bucket_env_var_is_set
	_run_test "${script}" _test_copies_single_file_with_copyto
	_run_test "${script}" _test_syncs_directory_with_rm_and_copy
	_run_test "${script}" _test_target_path_is_prefixed_with_temp
	_run_test "${script}" _test_uses_liferay_overlay_bucket_name_when_set
	_run_test "${script}" _test_prunes_orphan_when_into_dropped_from_manifest
	_run_test "${script}" _test_writes_prev_manifest_after_sync

	echo ""
	echo "Results: ${pass} passed, ${fail} failed."

	if [ "${fail}" -eq 0 ]
	then
		return 0
	fi

	return 1
}

function rclone {
	echo "rclone ${*}"
}

function _run_test {
	local script="${1}"
	local test_function="${2}"

	local description

	description=$(echo "${test_function}" | sed "s/^_test_//; s/_/ /g")

	local exit_code=0

	"${test_function}" "${script}" || exit_code="${?}"

	if [ "${exit_code}" -eq 0 ]
	then
		echo "PASS: ${description}."

		pass=$((pass + 1))
	else
		echo "FAIL: ${description}."

		fail=$((fail + 1))
	fi
}

function _tmp_temp_dir {
	local dir

	dir=$(mktemp -d)

	mkdir -p "${dir}/temp"

	echo "${dir}"
}

function _run_script {
	local script="${1}"
	local sandbox="${2}"
	local manifest="${3}"

	shift 3

	LIFERAY_OVERLAY_BUCKET_NAME="test-bucket" bash -c "
		set -o errexit

		# Redirect /temp writes into the sandbox so tests can inspect them.
		function rclone { echo \"rclone \${*}\"; }
		export -f rclone

		sed 's|/temp/|${sandbox}/temp/|g; s|PREV_MANIFEST=/temp/|PREV_MANIFEST=${sandbox}/temp/|' '${script}' > '${sandbox}/overlay-sync.sh'

		bash '${sandbox}/overlay-sync.sh' gcs '${manifest}'
	" "$@" 2>&1
}

function _test_exits_with_error_when_argument_count_is_wrong {
	if [[ "$(bash "${1}" gcs 2>&1)" == *"Usage:"* ]]
	then
		return 0
	fi

	return 1
}

function _test_exits_with_error_when_no_bucket_env_var_is_set {
	local sandbox manifest

	sandbox=$(_tmp_temp_dir)
	manifest="${sandbox}/manifest"
	printf 'source/path\tdest/path\n' > "${manifest}"

	if [[ "$(unset LIFERAY_OVERLAY_BUCKET_NAME; bash "${1}" gcs "${manifest}" 2>&1)" == *"Overlay bucket does not exist"* ]]
	then
		rm -rf "${sandbox}"

		return 0
	fi

	rm -rf "${sandbox}"

	return 1
}

function _test_copies_single_file_with_copyto {
	local sandbox manifest output

	sandbox=$(_tmp_temp_dir)
	manifest="${sandbox}/manifest"
	printf 'source/file.config\tosgi/configs/file.config\n' > "${manifest}"

	output=$(_run_script "${1}" "${sandbox}" "${manifest}")

	rm -rf "${sandbox}"

	if [[ "${output}" == *"rclone copyto :gcs,env_auth=true:test-bucket/source/file.config"* ]]
	then
		return 0
	fi

	return 1
}

function _test_syncs_directory_with_rm_and_copy {
	local sandbox manifest output

	sandbox=$(_tmp_temp_dir)
	manifest="${sandbox}/manifest"
	printf 'source/dir/\tosgi/modules\n' > "${manifest}"

	output=$(_run_script "${1}" "${sandbox}" "${manifest}")

	rm -rf "${sandbox}"

	if [[ "${output}" == *"rclone copy :gcs,env_auth=true:test-bucket/source/dir/"* ]] && [[ "${output}" != *"rclone copyto"* ]]
	then
		return 0
	fi

	return 1
}

function _test_target_path_is_prefixed_with_temp {
	local sandbox manifest output

	sandbox=$(_tmp_temp_dir)
	manifest="${sandbox}/manifest"
	printf 'source/path\tosgi/configs\n' > "${manifest}"

	output=$(_run_script "${1}" "${sandbox}" "${manifest}")

	rm -rf "${sandbox}"

	if [[ "${output}" == *"${sandbox}/temp/osgi/configs"* ]]
	then
		return 0
	fi

	return 1
}

function _test_uses_liferay_overlay_bucket_name_when_set {
	local sandbox manifest output

	sandbox=$(_tmp_temp_dir)
	manifest="${sandbox}/manifest"
	printf 'source/path\tdest/path\n' > "${manifest}"

	output=$(_run_script "${1}" "${sandbox}" "${manifest}")

	rm -rf "${sandbox}"

	if [[ "${output}" == *":gcs,env_auth=true:test-bucket/source/path"* ]]
	then
		return 0
	fi

	return 1
}

function _test_prunes_orphan_when_into_dropped_from_manifest {
	local sandbox manifest orphan output

	sandbox=$(_tmp_temp_dir)
	manifest="${sandbox}/manifest"
	orphan="${sandbox}/temp/osgi/configs/OldConfig.config"

	mkdir -p "$(dirname "${orphan}")"
	echo "orphan-content" > "${orphan}"

	printf 'osgi/configs/OldConfig.config\n' > "${sandbox}/temp/.overlay-manifest"

	printf 'source/keep.config\tosgi/configs/keep.config\n' > "${manifest}"

	output=$(_run_script "${1}" "${sandbox}" "${manifest}")

	local result=1

	if [ ! -e "${orphan}" ] && [[ "${output}" == *"Removed orphan"* ]]
	then
		result=0
	fi

	rm -rf "${sandbox}"

	return "${result}"
}

function _test_writes_prev_manifest_after_sync {
	local sandbox manifest output

	sandbox=$(_tmp_temp_dir)
	manifest="${sandbox}/manifest"
	printf 'src/a\tosgi/configs/a\n' > "${manifest}"
	printf 'src/b\tosgi/configs/b\n' >> "${manifest}"

	output=$(_run_script "${1}" "${sandbox}" "${manifest}")

	local result=1

	if [ -f "${sandbox}/temp/.overlay-manifest" ] \
		&& grep -qx 'osgi/configs/a' "${sandbox}/temp/.overlay-manifest" \
		&& grep -qx 'osgi/configs/b' "${sandbox}/temp/.overlay-manifest"
	then
		result=0
	fi

	rm -rf "${sandbox}"

	return "${result}"
}

main "${@}"