#! /bin/sh

# Must build with JDK8
export JAVA_HOME=/usr/java/jdk1.8.0_191-amd64

# Set Tomcat version
export TOMCAT=8.5.35

echo "TOMCAT: $TOMCAT"

# Set Sakai Version
VERSION=12.x

# Paths
MYPATH=/usr/local/src/vula-$VERSION
DISTFILES=$MYPATH/distfiles
LOCALCONFIG=$MYPATH/config
DEST=$MYPATH/apache-tomcat-$TOMCAT
PATCHES=$MYPATH/patches
PATCHES_FILE=$PATCHES/patches.txt
LOCALMAVENREPO=$MYPATH/.m2
MAVEN_PATH=/usr/local/apache-maven-3.3.9
SKIN_PATH=/usr/local/src/vula-skin-$VERSION

# Source directory
SAKAISRC=/usr/local/src/vula_src/branches/vula-$VERSION

unset MAVEN_OPTS

echo "LOCALMAVENREPO: $LOCALMAVENREPO"
echo "Sakai Version: $VERSION"
echo "Sakai Source: $SAKAISRC"
echo

