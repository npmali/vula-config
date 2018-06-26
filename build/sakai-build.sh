#! /bin/sh

# Build Vula from source
DIRNAME=$(dirname "$0")
PROGNAME=$(basename "$0")

BUILD_HOME=$PWD

source ./profile.sh

ERROR="########################## VULA BUILD FAILED ##########################"
LOCALMAVENREPO=$MYPATH/.m2

printf "Building Vula $VERSION from source directories $SAKAISRC"

if [ "$1" = "notest" ]
then
	MVNTEST="-Dmaven.test.skip=true"
        printf " (No Tests)\n"
else
	MVNTEST=""
	printf " (With Tests)\n"
fi

echo

mvn --version

# Copy target Sakai source to build directory
echo
echo Copying Sakai source to build directory

rm -rf sakai

# rsync is much faster than cp because we can exclude the git stuff
rsync -a  $SAKAISRC/ sakai2 --exclude .git --delete

echo "Done copying Sakai src.."

# Extract Tomcat
rm -rf $DEST

echo "Extracting Tomcat..."
tar zxf $DISTFILES/apache-tomcat-$TOMCAT.tar.gz

# remove the example dirs in tomcat VULA-1043
rm -rf $DEST/webapps/docs
rm -rf $DEST/webapps/examples
rm -rf $DEST/webapps/host-manager
rm -rf $DEST/webapps/manager

# Copy the skin, so direct connections to Tomcat still get CSS

echo "Copying Sakai skin to src reference"
cp -r /srv/www/vhosts/devslscle001.uct.ac.za/library/skin/vula sakai2/library/src/webapp/skin/
cp -r /srv/www/vhosts/devslscle001.uct.ac.za/library/skin/vula-gateway sakai2/library/src/webapp/skin/
echo "Done copying skin"

# Copy the src mods
cp -R src_mods/* sakai2/

# Get the versions (git last commit)
LOCALVER=`cd $SAKAISRC ; git rev-parse --short=7 HEAD`
SAKAIVER=12.x

cd $BUILD_HOME/sakai2

echo
echo "##### Apply patches"
echo

#function to halt build process on patching error
stop_build()
{
   echo ""
   echo "[ERROR] Cannot apply patch: $1"
   echo ""
   echo $ERROR
   exit 1
}

# make sure patch file exist and readable
if [ ! -f $PATCHES_FILE ]
   then
   	echo $ERROR	
  	echo "[ERROR] $PATCHES_FILE : does not exists"
  	exit 1
   elif [ ! -r $PATCHES_FILE ]
   then
  	echo $ERROR
  	echo "[ERROR] $PATCHES_FILE: can not read"
  	exit 2
   fi

# read $PATCHES_FILE using the file descriptors
# Set loop separator to end of line
ORIGIFS=$IFS

exec 3<&0
exec 0<$PATCHES_FILE
while read line
do
	if [ "$line" ] && [ ${line:0:1} != '#' ]
	then
		PNUM=`echo $line | cut -d \  -f 1`
		PATCH=`echo $line | cut -d \  -f 2`

		#Confirm this is a patch file by checking the last part of string for ".diff" 
		if [ ${PATCH:(-5)} == '.diff' ] 
		then
			echo "[patch] Applying: $PATCH"
			patch -$PNUM --ignore-whitespace < $PATCHES/$PATCH;
			if [ "$?" != "0" ]
		    then
		  	   stop_build $PATCH
		    else 
		       echo "[done]"
			fi
		else
			echo "[info] $PATCH is not a valid patch file."
		  	stop_build $PATCH
		fi
	fi
done
exec 0<&3
# restore $IFS which was used to determine what the field separators are
IFS=$ORIGIFS

echo
echo "##### Finished patching"

read -t2

# Build Sakai

#Maven2 build command

mvn -Dmaven.tomcat.home=$DEST -Dmaven.repo.local=$LOCALMAVENREPO/repository/ $MVNTEST clean install sakai:deploy

# Almost finished
cd ..

# Record version
BUILDTIME=`date +%y%m%d-%H%M`
echo $BUILDTIME > latest-build-time
echo $SAKAIVER-$LOCALVER > latest-build-version

echo "[OK]"

# SAM-1537
mkdir -p $DEST/sakai/samigo/answerUploadRepositoryPath/

# Flag executable
chmod +x apache-tomcat-$TOMCAT/bin/*.sh

