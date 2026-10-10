#!/usr/bin/env bash
# Copyright 2026 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Installs the spanner-fraud-demo Gemini CLI extension. It connects Gemini CLI
# to the Spanner remote MCP server (read-only tools only) and tells Gemini
# which database to query. Uses the same variables as setup.sh.
# Gemini CLI authenticates to the MCP server with Application Default
# Credentials: run `gcloud auth application-default login` first.

set -euo pipefail

cd "$(dirname "$0")"

PROJECT_ID="${PROJECT_ID:-$(gcloud config get-value project 2>/dev/null)}"
SPANNER_INSTANCE="${SPANNER_INSTANCE:-fraud-demo-instance}"
SPANNER_DATABASE="${SPANNER_DATABASE:-fraud-graph-db-v2}"
DEST="${HOME}/.gemini/extensions/spanner-fraud-demo"

if [[ -z "${PROJECT_ID}" ]]; then
  echo "Set PROJECT_ID or run: gcloud config set project YOUR_PROJECT" >&2
  exit 1
fi

mkdir -p "${DEST}"
for file in gemini-extension.json GEMINI.md; do
  sed -e "s|@PROJECT_ID@|${PROJECT_ID}|g" \
      -e "s|@SPANNER_INSTANCE@|${SPANNER_INSTANCE}|g" \
      -e "s|@SPANNER_DATABASE@|${SPANNER_DATABASE}|g" \
      "${file}" > "${DEST}/${file}"
done

echo "Installed ${DEST}."
echo "Start Gemini CLI and run /mcp to check that the spanner server is connected."
echo "To remove it: rm -rf ${DEST}"
