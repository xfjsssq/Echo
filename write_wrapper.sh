#!/usr/bin/env bash
JAVA_EXE="/c/Program Files/Eclipse Adoptium/jdk-17.0.19.10-hotspot/bin/java.exe"
TESTC="D:\\Agent\\Agent  workplace\\Claude Code工作区\\Echo\\app\\build\\intermediates\\classes\\debugUnitTest\\transformDebugUnitTestClassesWithAsm\\dirs"
JUNIT="C:\\Users\\knowingly\\.gradle\\caches\\modules-2\\files-2.1\\junit\\junit\\4.13.2\\8ac9e16d933b6fb43bc7f576336b8f4d7eb5ba12\\junit-4.13.2.jar"
HAMCREST="C:\\Users\\knowingly\\.gradle\\caches\\modules-2\\files-2.1\\org.hamcrest\\hamcrest-core\\1.3\\42a25dc3219429f0e5d060061f71acb49bf010a0\\hamcrest-core-1.3.jar"
WRAPPER="D:\\Agent\\Agent  workplace\\Claude Code工作区\\Echo\\test_runner.bat"

cat > "$WRAPPER" <<EOF
@echo off
"%JAVA_EXE%" -classpath "%TESTC%;%JUNIT%;%HAMCREST%" org.junit.runner.JUnitCore %*
EOF
cat "$WRAPPER"
