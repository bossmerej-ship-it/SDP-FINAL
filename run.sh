#!/bin/sh
set -eu
cd "$(dirname "$0")"
mkdir -p out
find src/main/java -name '*.java' | sort > out/sources.txt
javac --release 17 -encoding UTF-8 -Xlint:all -d out @out/sources.txt
java -cp out restaurant.Demo
