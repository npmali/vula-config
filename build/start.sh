#!/bin/bash

/etc/init.d/sakai12 start
tail -f /usr/local/sakai12a/logs/catalina.out
