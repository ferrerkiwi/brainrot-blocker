@ECHO OFF
SET DIRNAME=%~dp0
IF "%DIRNAME%" == "" SET DIRNAME=.
SET CLASSPATH=%DIRNAME%\gradle\wrapper\gradle-wrapper.jar

java -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
