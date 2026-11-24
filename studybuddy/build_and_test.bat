@echo off
rem Compiles StudyBuddy and runs all test classes. Run from the project folder.
if exist out rmdir /s /q out
mkdir out
dir /s /b src\*.java test\*.java > sources.txt
javac -d out @sources.txt || exit /b 1
echo Compiled OK.
for %%T in (Section1Test Section2Test Section3Test Section4Test Section5Test Section6Test Section7Test Section8Test) do (
  echo --- %%T
  java -cp out %%T | findstr /R "FAIL RESULT"
)
