#!/bin/sh

# Minimal Gradle launcher for this single-module Android project.
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

exec java -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
