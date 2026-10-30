#!/usr/bin/env bash
# Compile everything, run Tests, then Main.
set -euo pipefail
rm -rf out
javac -d out $(find src -name "*.java")
echo "== Tests"
java -cp out app.Tests
echo "== Main"
java -cp out app.Main
