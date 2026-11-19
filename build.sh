#!/usr/bin/env bash
# Builds the modulith. Needs ff-logistic-base and ff-logistic-shared installed first
# (../base/build.sh, then ../shared/build.sh): the business areas come from shared's domain cores.
#
#   ./build.sh
#   ./build.sh -DskipTests
set -euo pipefail
cd "$(dirname "$0")"
./mvnw -B install "$@"
