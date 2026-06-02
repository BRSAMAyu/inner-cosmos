#!/bin/sh
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on
# an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------

# ----------------------------------------------------------------------------
# Maven Start Up Batch script
#
# Required ENV vars:
# ------------------
#   JAVA_HOME - location of a JDK home dir
#
# Optional ENV vars
# -----------------
#   M2_HOME - location of maven2's installed home dir
#   MAVEN_OPTS - parameters passed to the Java VM when running Maven
#     e.g. to debug Maven itself, use
#       set MAVEN_OPTS=-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=:5005
#   MAVEN_SKIP_RC - flag to disable loading of mavenrc files
# ----------------------------------------------------------------------------

if [ -z "$MAVEN_SKIP_RC" ] ; then

  if [ -f /usr/local/etc/mavenrc ] ; then
    . /usr/local/etc/mavenrc
  fi

  if [ -f /etc/mavenrc ] ; then
    . /etc/mavenrc
  fi

  if [ -f "$HOME/.mavenrc" ] ; then
    . "$HOME/.mavenrc"
  fi

fi

# OS specific support.  $var _must_ be set to either true or false.
cygwin=false;
darwin=false;
mingw=false
case "`uname`" in
  CYGWIN*) cygwin=true ;;
  Darwin*) darwin=true
    if [ -z "$JAVA_VERSION" ] ; then
      JAVA_VERSION="CurrentJDK"
    else
      echo "Warning: JAVA_VERSION should be a JDK version, not a directory"
    fi
    ;;
  MINGW*) mingw=true ;;
esac

# For Cygwin, ensure paths are in UNIX format before anything is touched
if $cygwin ; then
  [ -n "$M2_HOME" ] &&
    M2_HOME="`cygpath --unix "$M2_HOME"`"
  [ -n "$JAVA_HOME" ] &&
    JAVA_HOME="`cygpath --unix "$JAVA_HOME"`"
  # For Cygwin, fix paths for Maven
  [ -n "$M2_HOME" ] &&
    M2_HOME="`cygpath --path --windows "$M2_HOME"`"
  [ -n "$JAVA_HOME" ] &&
    JAVA_HOME="`cygpath --path --windows "$JAVA_HOME"`"
fi

# For Mingw, ensure paths are in UNIX format before anything is touched
if $mingw ; then
  [ -n "$M2_HOME" ] &&
    M2_HOME="`(
      cd "$M2_HOME"
      pwd -W | tr -d '[[:space:]]'
    )`"
  [ -n "$JAVA_HOME" ] &&
    JAVA_HOME="`(
      cd "$JAVA_HOME"
      pwd -W | tr -d '[[:space:]]'
    )`"
fi

if [ -z "$JAVA_HOME" ] ; then
  javaExecutable="`which javac`"
else
  javaExecutable="$JAVA_HOME/bin/javac"
fi

if [ ! -e "$javaExecutable" ] ; then
  echo "Error: JAVA_HOME is not defined correctly" >&2
  echo "  We cannot execute $javaExecutable" >&2
  exit 1
fi

# For Cygwin, switch paths to Windows format before running java
if $cygwin ; then
  [ -n "$M2_HOME" ] &&
    M2_HOME="`cygpath --path --windows "$M2_HOME"`"
  [ -n "$JAVA_HOME" ] &&
    JAVA_HOME="`cygpath --path --windows "$JAVA_HOME"`"
fi

# For Mingw, ensure paths are in UNIX format before anything is touched
if $mingw ; then
  [ -n "$M2_HOME" ] &&
    M2_HOME="`(
      cd "$M2_HOME"
      pwd
    )`"
  [ -n "$JAVA_HOME" ] &&
    JAVA_HOME="`(
      cd "$JAVA_HOME"
      pwd
    )`"
fi

# echo "JAVA_HOME=$JAVA_HOME"
# echo "M2_HOME=$M2_HOME"
# echo "MAVEN_OPTS=$MAVEN_OPTS"

# For Cygwin, switch paths to Windows format before running java
if $cygwin ; then
  SEARCH="$M2_HOME"
  SAVEIFS=$IFS
  IFS=$IFS:
  for dir in $SEARCH ; do
    [ -d "$dir" ] && M2_HOME="$M2_HOME `cygpath --path --windows "$dir"`"
  done
  IFS=$SAVEIFS

  SEARCH="$JAVA_HOME"
  SAVEIFS=$IFS
  IFS=$IFS:
  for dir in $SEARCH ; do
    [ -d "$dir" ] && JAVA_HOME="$JAVA_HOME `cygpath --path --windows "$dir"`"
  done
  IFS=$SAVEIFS
fi

# Add M2_HOME to the path if it's not already present
if $mingw ; then
  PATH="$M2_HOME/bin:$PATH"
else
  PATH="$M2_HOME/bin:$PATH"
fi

# echo "PATH=$PATH"

# Provide a sane default for JAVA_HOME
if [ -z "$JAVA_HOME" ]; then
  # If java executable is in the path, use it to determine JAVA_HOME
  if [ -n "$javaExecutable" ] ; then
    # Get the canonical path to the java executable
    JAVA_HOME=`dirname "$javaExecutable"`
    JAVA_HOME=`dirname "$JAVA_HOME"`
    export JAVA_HOME
  fi
fi

# Default M2_HOME to the user's home directory if not set
if [ -z "$M2_HOME" ] ; then
  export M2_HOME="$HOME/.m2"
fi

DEFAULT_MAVEN_OPTS="-Dfile.encoding=UTF-8"
if [ -z "$MAVEN_OPTS" ] ; then
  MAVEN_OPTS="$DEFAULT_MAVEN_OPTS"
fi

# echo "Using Maven: $M2_HOME"
# echo "Using Java: $JAVA_HOME"

# Convert a Maven path to a Windows path (reverse of cygpath)
# Requires cygpath.  If not available, just return the path as-is.
convertMavenPath() {
  if $cygwin ; then
    "$JAVA_HOME/bin/cygpath" -u -w "$1"
  else
    echo "$1"
  fi
}

# Endorsers are used to locate Maven plugins, especially
# when the plugin versions are not specified in the POM.
# Default to using the Maven repository cached in the local
# Maven home directory, if it exists. If it does not, use the
# Maven repository from the system Maven installation.
# Note: The endorser settings should not be located within
# ~/.m2/profile.xml because that file is intended to be used
# by developers as a convenient way to override Maven settings.
# Developers might inadvertently override the endorser settings by
# creating or editing their ~/.m2/profile.xml, causing this script
# to inadvertently locate the system Maven installation instead
# of the Maven wrapper.  This could lead to unintended
# behavior, such as the system Maven installation being used
# instead of the Maven wrapper.
# Therefore, the endorser settings should be located within the
# Maven wrapper itself, within the .mvn/wrapper/maven-wrapper.properties
# file.
DEFAULT_MAVEN_ENDORSED="${M2_HOME}/.m2/toolchains.xml"

# Endorsers are used to locate Maven plugins, especially
# when the plugin versions are not specified in the POM.
# Default to using the Maven repository cached in the local
# Maven home directory, if it exists. If it does not, use the
# Maven repository from the system Maven installation.
# Note: The endorser settings should not be located within
# ~/.m2/profile.xml because that file is intended to be used
# by developers as a convenient way to override Maven settings.
# Developers might inadvertently override the endorser settings by
# creating or editing their ~/.m2/profile.xml, causing this script
# to inadvertently locate the system Maven installation instead
# of the Maven wrapper.  This could lead to unintended
# behavior, such as the system Maven installation being used
# instead of the Maven wrapper.
# Therefore, the endorser settings should be located within the
# Maven wrapper itself, within the .mvn/wrapper/maven-wrapper.properties
# file.
DEFAULT_MAVEN_ENDORSED="${M2_HOME}/.m2/toolchains.xml"

# Set JAVACMD to the java executable
if [ -z "$JAVACMD" ] ; then
  JAVACMD="$JAVA_HOME/bin/java"
fi

# Execute Maven using the mvnw script
exec "$JAVACMD" \
  $MAVEN_OPTS \
  $MAVEN_DEBUG_OPTS \
  -classpath "$(convertMavenPath "$M2_HOME/.mvn/wrapper/maven-wrapper.jar")" \
  "-Dmaven.home=$(convertMavenPath "$M2_HOME")" \
  "-Dmaven.multiModuleProjectDirectory=$(convertMavenPath "$M2_HOME/.mvn/wrapper")" \
  org.apache.maven.wrapper.MavenWrapperMain \
  "$@"
