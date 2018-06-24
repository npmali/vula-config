#! /bin/bash

HIGHLIGHT="\e[01;34m"
NORMAL='\e[00m'

find sakai -name .git -type d -prune | while read d; do
   cd $d/..
   echo -e "\n${HIGHLIGHT}Updating ${d::${#d}-4} ${NORMAL}"
   ##git fetch --all && git pull
   cd $OLDPWD
done
