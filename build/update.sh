#! /bin/sh

# Build Vula from source
DIRNAME=$(dirname "$0")
PROGNAME=$(basename "$0")

source ./profile.sh

echo "Update source to newest version ($SAKAISRC) [$VERSION]"

cd $SAKAISRC
git fetch && git pull
git-external update
cd $MYPATH 
