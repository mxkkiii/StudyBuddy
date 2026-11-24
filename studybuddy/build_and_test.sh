#!/bin/bash
# Compiles StudyBuddy and runs all test classes. Run from the project folder.
set -e
rm -rf out && mkdir out
if command -v javac >/dev/null 2>&1; then
  javac -d out $(find src test -name '*.java')
else
  java -m jdk.compiler/com.sun.tools.javac.Main -d out $(find src test -name '*.java')   # JRE-only fallback
fi
echo "Compiled OK."
for t in Section1Test Section2Test Section3Test Section4Test Section5Test Section6Test Section7Test Section8Test; do
  echo "--- $t"
  java -cp out $t | grep -E "FAIL|RESULT"
done
