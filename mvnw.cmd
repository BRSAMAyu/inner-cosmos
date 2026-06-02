@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    https://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on
@REM an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Maven Start Up Batch script
@REM
@REM Required ENV vars:
@REM ------------------
@REM   JAVA_HOME - location of a JDK home dir
@REM
@REM Optional ENV vars
@REM -----------------
@REM   M2_HOME - location of maven2's installed home dir
@REM   MAVEN_OPTS - parameters passed to the Java VM when running Maven
@REM     e.g. to debug Maven itself, use
@REM       set MAVEN_OPTS=-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=:5005
@REM   MAVEN_SKIP_RC - flag to disable loading of mavenrc files
@REM ----------------------------------------------------------------------------

@REM Begin all REM lines with '@@' to avoid problems when lines are wrapped
@@setlocal

@REM enable expansion by the command processor so that environment variables can be used
@setlocal enableextensions

@REM If MAVEN_SKIP_RC is set, skip the environment checks
if not "%MAVEN_SKIP_RC%" == "" goto skip_rc_env

@REM Set environment variable naming conventions to avoid conflicts with other tools
@set "var_M2_HOME=%M2_HOME%"
:label_m2_home
@REM If M2_HOME is not set, use the default
@if "%var_M2_HOME%" == "" (
  @REM Try to find Maven from installation directory
  if exist "%USERPROFILE%" (
    @set "var_M2_HOME=%USERPROFILE%\.m2"
  )
)

@REM If JAVA_HOME is not set, try to find it
if "%JAVA_HOME%" == "" goto no_java_home
goto got_java_home

:label_m2_home
@REM If M2_HOME is not set, use the default
@if "%var_M2_HOME%" == "" (
  @set "var_M2_HOME=%USERPROFILE%\.m2"
)

@REM Make sure the M2_HOME directory exists
@if not exist "%var_M2_HOME%" goto no_m2_home

@got_java_home
@REM Validate JAVA_HOME
if not exist "%JAVA_HOME%\bin\java.exe" goto no_java_home
if not exist "%JAVA_HOME%\bin\javac.exe" goto no_java_home

@REM Make a backup of the current CLASSPATH
@set "var_CLASSPATH=%CLASSPATH%"

@REM Call Maven
@set "CLASSPATH_WRAPPER=%CLASSPATH%"
@set "CLASSPATH=%CLASSPATH_WRAPPER%;%var_M2_HOME%\.mvn\wrapper\maven-wrapper.jar"
@set "MAVEN_PROJECTBASEDIR=%~dp0"
if not "%MAVEN_PROJECTBASEDIR%" == "" goto end_init_maven

@REM Expand CLASSPATH to include Maven wrapper jar
@set "CLASSPATH=%CLASSPATH%;%var_M2_HOME%\.mvn\wrapper\maven-wrapper.jar"

@REM Execute Maven
@set "MAVEN_OPTS=%DEFAULT_MAVEN_OPTS% %MAVEN_OPTS%"
@set "MAVEN_DEBUG_OPTS=-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=:5005"
@set "MAVEN_OPTS=%MAVEN_OPTS% %MAVEN_DEBUG_OPTS%"

@REM If MAVEN_OPTS is empty, use default
if "%MAVEN_OPTS%" == "" set "MAVEN_OPTS=-Dfile.encoding=UTF-8"

@REM Run Maven
"%JAVA_HOME%\bin\java.exe" %MAVEN_OPTS% -classpath "%var_M2_HOME%\.mvn\wrapper\maven-wrapper.jar" "-Dmaven.home=%var_M2_HOME%" "-Dmaven.multiModuleProjectDirectory=%var_M2_HOME%\.mvn\wrapper" org.apache.maven.wrapper.MavenWrapperMain %MAVEN_CONFIG% %*

@goto end

@REM -----------------------------------------------------------------------------
@REM error handling
:no_java_home
@echo Error: JAVA_HOME is not set correctly
@echo Please set the JAVA_HOME variable in your environment to match the
@echo location of your Java installation
@goto end

:no_m2_home
@echo Error: M2_HOME is not set correctly
@echo Please set the M2_HOME variable in your environment to match the
@echo location of your Maven installation
@echo Alternatively, you can create a .m2 directory in your user home directory
@goto end

@REM -----------------------------------------------------------------------------
:end_init_maven
@REM Set the project base directory
if "%MAVEN_PROJECTBASEDIR%" == "" set "MAVEN_PROJECTBASEDIR=%WDP%"
if not exist "%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar" (
  @echo Error: Maven wrapper JAR not found
  @echo Please run 'mvn -N io.takari:maven:0.7.6:wapper' to install the Maven wrapper
  @goto end
)

@REM Make a backup of the current CLASSPATH
@set "var_CLASSPATH=%CLASSPATH%"

@REM Expand CLASSPATH to include Maven wrapper jar
@set "CLASSPATH=%CLASSPATH%;%var_M2_HOME%\.mvn\wrapper\maven-wrapper.jar"

@REM Execute Maven
@set "MAVEN_OPTS=%DEFAULT_MAVEN_OPTS% %MAVEN_OPTS%"
@set "MAVEN_DEBUG_OPTS=-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=:5005"
@set "MAVEN_OPTS=%MAVEN_OPTS% %MAVEN_DEBUG_OPTS%"

@REM If MAVEN_OPTS is empty, use default
if "%MAVEN_OPTS%" == "" set "MAVEN_OPTS=-Dfile.encoding=UTF-8"

@REM Run Maven
"%JAVA_HOME%\bin\java.exe" %MAVEN_OPTS% -classpath "%var_M2_HOME%\.mvn\wrapper\maven-wrapper.jar" "-Dmaven.home=%var_M2_HOME%" "-Dmaven.multiModuleProjectDirectory=%var_M2_HOME%\.mvn\wrapper" org.apache.maven.wrapper.MavenWrapperMain %MAVEN_CONFIG% %*

@REM Restore the CLASSPATH
@set "CLASSPATH=%var_CLASSPATH%"

:end
@REM set local scope for the variables with windows NT shell
@if "%OS%"=="Windows_NT" @endlocal
@if "%OS%"=="WINNT" @endlocal

@REM pause if optional batch parameter was provided
@if "%*"=="-pause" @pause