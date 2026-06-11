#!/bin/bash
# Run all tests — Java/Kotlin, JavaScript, and checks.
# Usage: bash verify.sh

set -e

echo "=== Maven test ==="
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

echo ""
echo "=== Node.js syntax check ==="
node --check src/main/resources/static/app.js

echo ""
echo "=== Node.js unit tests ==="
node --test src/test/js/normalizeDiscoveryResultSummary.test.js

echo ""
echo "=== Git diff whitespace check ==="
git diff --check

echo ""
echo "All checks passed."
