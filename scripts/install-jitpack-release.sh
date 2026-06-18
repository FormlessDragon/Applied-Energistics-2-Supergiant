#!/usr/bin/env bash
set -euo pipefail

version="${VERSION:-${RELEASE_TAG:-}}"
if [[ -z "${version}" ]]; then
  echo "Missing VERSION or RELEASE_TAG" >&2
  exit 1
fi

if [[ -n "${GITHUB_REPOSITORY:-}" ]]; then
  github_repository="${GITHUB_REPOSITORY}"
else
  : "${GROUP:?Missing GROUP}"
  : "${ARTIFACT:?Missing ARTIFACT}"
  owner="${GROUP#com.github.}"
  if [[ "${owner}" == "${GROUP}" || -z "${owner}" ]]; then
    echo "GROUP must use JitPack's com.github.<owner> format, got: ${GROUP}" >&2
    exit 1
  fi
  github_repository="${owner}/${ARTIFACT}"
fi

target_repository="${MAVEN_LOCAL_REPOSITORY:-${HOME}/.m2/repository}"
temp_dir="${RUNNER_TEMP:-${TMPDIR:-/tmp}}"

if [[ -n "${RELEASE_TAG:-}" ]]; then
  release_tag="${RELEASE_TAG}"
elif [[ "${version}" == v* ]]; then
  release_tag="${version}"
else
  release_tag="v${version}"
fi

asset_name="${JITPACK_REPOSITORY_ASSET:-jitpack-repository-${release_tag}.zip}"
archive_path="${temp_dir}/${asset_name}"

mkdir -p "$(dirname "${archive_path}")" "${target_repository}"

if [[ -n "${GROUP:-}" ]]; then
  group_path="${GROUP//./\/}"
else
  owner="${github_repository%%/*}"
  group_path="com/github/${owner}"
fi

if [[ -n "${ARTIFACT:-}" ]]; then
  artifact_id="${ARTIFACT}"
else
  artifact_id="${github_repository##*/}"
fi

expected_pom="${target_repository}/${group_path}/${artifact_id}/${version}/${artifact_id}-${version}.pom"
expected_dev_jar="${target_repository}/${group_path}/${artifact_id}/${version}/${artifact_id}-${version}-dev.jar"
expected_main_jar="${target_repository}/${group_path}/${artifact_id}/${version}/${artifact_id}-${version}.jar"
expected_sources_jar="${target_repository}/${group_path}/${artifact_id}/${version}/${artifact_id}-${version}-sources.jar"
expected_javadoc_jar="${target_repository}/${group_path}/${artifact_id}/${version}/${artifact_id}-${version}-javadoc.jar"
project_artifact_dir="${PWD}/build/libs"
project_repository_dir="${PWD}/build/jitpack-repository"
project_pom="${PWD}/build/pom.xml"
project_libs_pom="${project_artifact_dir}/pom.xml"
project_publication_pom="${PWD}/build/publications/mavenJava/pom-default.xml"

download_release_asset() {
  local tag="$1"
  local name="$2"
  local url="https://github.com/${github_repository}/releases/download/${tag}/${name}"

  echo "Downloading ${url}"
  curl -fL -sS \
    --output "${archive_path}" \
    "${url}"
}

if [[ -n "${JITPACK_REPOSITORY_URL:-}" ]]; then
  echo "Downloading ${JITPACK_REPOSITORY_URL}"
  curl -fL -sS \
    --output "${archive_path}" \
    "${JITPACK_REPOSITORY_URL}"
else
  if ! download_release_asset "${release_tag}" "${asset_name}"; then
    if [[ "${release_tag}" != "${version}" ]]; then
      fallback_asset_name="jitpack-repository-${version}.zip"
      archive_path="${temp_dir}/${fallback_asset_name}"
      download_release_asset "${version}" "${fallback_asset_name}"
      asset_name="${fallback_asset_name}"
    else
      exit 1
    fi
  fi
fi

echo "Installing JitPack repository into ${target_repository}"
echo "JitPack coordinates: ${group_path}/${artifact_id}/${version}"
echo "Working directory: ${PWD}"
if command -v git >/dev/null 2>&1; then
  git rev-parse --show-toplevel || true
fi
mkdir -p \
  "${target_repository}" \
  "${project_repository_dir}" \
  "${project_artifact_dir}" \
  "${PWD}/build" \
  "$(dirname "${project_pom}")" \
  "$(dirname "${project_libs_pom}")" \
  "$(dirname "${project_publication_pom}")"
unzip -q -o "${archive_path}" -d "${target_repository}"
unzip -q -o "${archive_path}" -d "${project_repository_dir}"
missing_files=()
for expected_file in \
  "${expected_pom}" \
  "${expected_dev_jar}" \
  "${expected_main_jar}" \
  "${expected_sources_jar}" \
  "${expected_javadoc_jar}"
do
  if [[ ! -f "${expected_file}" ]]; then
    missing_files+=("${expected_file}")
  fi
done

if (( ${#missing_files[@]} > 0 )); then
  echo "Expected installed artifacts not found:" >&2
  printf '  %s\n' "${missing_files[@]}" >&2
  find "${target_repository}" -maxdepth 8 -type f | sort | sed -n '1,80p' >&2
  exit 1
fi

cp "${expected_main_jar}" "${project_artifact_dir}/"
cp "${expected_dev_jar}" "${project_artifact_dir}/"
cp "${expected_sources_jar}" "${project_artifact_dir}/"
cp "${expected_javadoc_jar}" "${project_artifact_dir}/"
cp "${expected_main_jar}" "${PWD}/build/"
cp "${expected_dev_jar}" "${PWD}/build/"
cp "${expected_sources_jar}" "${PWD}/build/"
cp "${expected_javadoc_jar}" "${PWD}/build/"
cp "${expected_pom}" "${project_pom}"
cp "${expected_pom}" "${project_libs_pom}"
cp "${expected_pom}" "${project_publication_pom}"

echo "Installed project build artifacts:"
find "${PWD}/build" -maxdepth 4 -type d | sort | sed -n '1,80p'
find "${PWD}/build" -maxdepth 8 -type f | sort | sed -n '1,120p'

find "${target_repository}" -maxdepth 8 -type f | sort | sed -n '1,80p'
