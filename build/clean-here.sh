#!/bin/sh

# Build Sakai from source (single folder)

# source directory
source /usr/local/src/vula-12.x/profile.sh

echo Cleaning folder in place

# Maven2 build commad
mvn $1 -Dmaven.tomcat.home=$DEST -Dmaven.test.skip=true -Dmaven.repo.local=$LOCALMAVENREPO/repository/ clean 

