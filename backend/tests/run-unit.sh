#!/usr/bin/env bash
# Runs every module's JUnit tests inside a Maven container (no local JDK/Maven needed).
set -euo pipefail
cd "$(dirname "$0")/.."

MSYS_NO_PATHCONV=1 docker run --rm \
  -v "$(pwd -W 2>/dev/null || pwd):/src" -v m2cache:/root/.m2 -w /src \
  maven:3.9-eclipse-temurin-17 \
  mvn -B -Dmaven.repo.local=/root/.m2/repository test
