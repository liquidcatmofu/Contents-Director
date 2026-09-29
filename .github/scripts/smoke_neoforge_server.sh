#!/usr/bin/env bash
set -euo pipefail

artifact_dir="${1:?Pass the downloaded artifact directory}"
mapfile -t universal_jars < <(find "$artifact_dir" -type f -name 'ContentsDirector-*-all.jar' \
  ! -name 'ContentsDirector-launchwrapper-*' ! -name 'ContentsDirector-modlauncher-*')
if [[ "${#universal_jars[@]}" -ne 1 ]]; then
  printf 'Expected one universal JAR, found %s\n' "${#universal_jars[@]}" >&2
  exit 1
fi

version='21.9.16-beta'
mkdir -p server/mods
cp "${universal_jars[0]}" server/mods/
cd server
curl --fail --location --retry 3 --output neoforge-installer.jar \
  "https://maven.neoforged.net/releases/net/neoforged/neoforge/${version}/neoforge-${version}-installer.jar"
java -jar neoforge-installer.jar --installServer > installer.log 2>&1 || {
  tail -n 100 installer.log >&2
  exit 1
}

# The first run exits at Minecraft's EULA prompt. It exercises FML's early
# discovery and Contents Director's server-side initialization without
# accepting the EULA or starting a persistent server in CI.
status=0
timeout 180s bash ./run.sh nogui > server.log 2>&1 || status=$?
if [[ "$status" -eq 124 || "$status" -eq 137 ]] || \
   ! grep -Fq 'Loading FML Early Services' server.log || \
   ! grep -Fq 'Detected side: SERVER' server.log || \
   ! grep -Fq 'eula.txt' server.log || \
   grep -Fq 'not a valid mod file' server.log; then
  tail -n 120 server.log >&2
  printf 'NeoForge server early startup failed (exit %s)\n' "$status" >&2
  exit 1
fi
printf 'NeoForge %s detected Contents Director on the dedicated server before the EULA prompt.\n' "$version"
