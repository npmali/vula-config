#!/ bin/sh

find . -type f | grep -v .svn | cut -c 3- | grep -v checkmods | ./checkmods.pl

