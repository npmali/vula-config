#! /bin/sh

# Sakai installation script

source ./profile.sh

VULA_VER=12
DEST=/usr/local/sakai12a
BUILDDIR=/data/builds
WHEREAMI=`pwd`

echo "Deploying new version $VULA_VER into $DEST from apache-tomcat-$TOMCAT"

## Shutdown if running - node 1

if [ -e $DEST/bin/shutdown.sh ] ;
then
	PID=`ps -u tomcat -F | grep "$DEST/temp" | awk '{print $2}'`

	if [ -z "$PID" ]; then
	  echo Process not running.
	else

	  echo Stopping PID $PID
	  /etc/rc.d/sakai12 stop

	  for (( c=1; c<=12; c++ ))
	  do
	    echo Sleeping 5s
	    sleep 5
	    RUNNING=`ps -p $PID | grep -v PID  | awk '{print $1}'`
	    if [ -z "$RUNNING" ]; then
	       break
	    fi
	  done

	  RUNNING=`ps -p $PID | grep -v PID  | awk '{print $1}'`
	  if [ -z "$RUNNING" ]; then
	     echo Process stopped.
	  else
	     echo Process did not stop - killing PID $PID
	     kill -9 $PID
	  fi
	fi
fi

# Remove old versions
rm -R $DEST

# Copy Tomcat environment
cp -R apache-tomcat-$TOMCAT $DEST

# Copy properties files and toolOrder.xml - note that security.properties is stored elsewhere, 
# defined in the security path in the startup script
cp -Rf config/sakai $DEST/

# Set the version of the local build in sakai.properties
BUILDTIME=`cat latest-build-time`
REVISION=`cat latest-build-version`
echo version.service = \[$REVISION\] >> $DEST/sakai/sakai.properties

# favicon
cp -f config/favicon.ico  $DEST/webapps/ROOT/

# Copy Tomcat config file
cp -f config/conf/* $DEST/conf/

# Copy Tomcat log4j
cp -f config/lib/* $DEST/lib/

# Put index.html in the Tomcat root to redirect to /portal
cp -f $DISTFILES/index.html $DEST/webapps/ROOT/
rm -f $DEST/webapps/ROOT/index.jsp

# Copy mysql connector
cp -f $DISTFILES/mysql-connector-java-8.0.11.jar $DEST/lib/
rm -f $DEST/lib/mariadb-java-client*

# Respondus Production Jar (VULA-2882 can't put both jars in the same build)
cp $DISTFILES/respondus-samigo-ldb-1.0.8-vula.jar $DEST/lib/

# Install WIRIS WAR - https://jira.cet.uct.ac.za/browse/VULA-999 / VULA-2774
cp $DISTFILES/pluginwiris_engine.war $DEST/webapps/

# change the ownership to tomcat
chown -R tomcat $DEST

# tar this build
cd $DEST
echo BUILDTIME is $BUILDTIME, REVISION is $REVISION
tar zcf $BUILDDIR/vula$VULA_VER-$BUILDTIME-$REVISION.tar.gz *

# Respondus dev JAR
rm $DEST/lib/respondus-samigo-ldb-1.0.8-vula.jar
cp $DISTFILES/respondus-samigo-ldb-1.0.8-devslscle001.jar $DEST/lib/

# copy the local.properties
cp -f $MYPATH/config/sakai/local.properties.test $DEST/sakai/local.properties

# symlink latest
if [ $DEST = "/usr/local/sakai12a" ] ; then
  rm $BUILDDIR/vula$VULA_VER-latest.tar.gz
  ln -s $BUILDDIR/vula$VULA_VER-$BUILDTIME-$REVISION.tar.gz $BUILDDIR/vula$VULA_VER-latest.tar.gz
fi

cd $WHEREAMI

# source ./skin-install.sh

