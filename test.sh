#!/bin/sh
set -eu
cd "$(dirname "$0")"
mkdir -p out
find src/main/java src/test/java -name '*.java' | sort > out/test-sources.txt
javac --release 17 -encoding UTF-8 -Xlint:all -d out @out/test-sources.txt
java -cp out restaurant.RestaurantTests
java -cp out restaurant.WebTests
