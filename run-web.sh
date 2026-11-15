#!/bin/sh
set -eu
cd "$(dirname "$0")"
mkdir -p out
find src/main/java -name '*.java' | sort > out/web-sources.txt
javac --release 17 -encoding UTF-8 -Xlint:all -d out @out/web-sources.txt
exec java -cp out restaurant.web.WebServer "${1:-8080}"
