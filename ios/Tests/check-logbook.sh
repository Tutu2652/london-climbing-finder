#!/bin/zsh
set -eu
cd "${0:A:h:h:h}"
CHECK_DIR=$(mktemp -d /tmp/climb-logbook-tests.XXXXXX)
trap 'rm -rf "$CHECK_DIR"' EXIT
xcrun swiftc -parse-as-library ios/ClimbingFinder/Core/Logbook.swift ios/Tests/LogbookChecks.swift -o "$CHECK_DIR/checks"
"$CHECK_DIR/checks"
xcrun swiftc -parse-as-library -module-name LogbookMigration -D LEGACY ios/Tests/Fixtures/LogbookBeforeMedia.swift ios/Tests/LogbookMigrationChecks.swift -o "$CHECK_DIR/seed"
xcrun swiftc -parse-as-library -module-name LogbookMigration ios/ClimbingFinder/Core/Logbook.swift ios/Tests/LogbookMigrationChecks.swift -o "$CHECK_DIR/migrate"
"$CHECK_DIR/seed" "$CHECK_DIR/logbook.store"
"$CHECK_DIR/migrate" "$CHECK_DIR/logbook.store"
"$CHECK_DIR/migrate" "$CHECK_DIR/logbook.store" verify
