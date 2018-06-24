#! /bin/bash
if [ "$BASH" = "" ] ;then echo "Please run with bash"; exit 1; fi

#source config-dist.sh
PORT=8443

if [ "$PORT" == "" ]; then 
    echo "Bad configuration or wrong shell"; 
    exit 
fi

PROCESS_ID=`lsof -i :$PORT | grep java | awk '{print $2}'`
if [ "$PROCESS_ID" == "" ]; then
    echo There is no process on $PORT
    return 2>/dev/null || exit
fi

if [ -f /etc/init.d/sakai12 ]; then 
    echo "Stopping Tomcat"
    /etc/init.d/sakai12 stop
fi

# wait 2 seconds and see if we are down
sleep 2
PROCESS_ID=`lsof -i :$PORT | grep java | awk '{print $2}'`
if [ "$PROCESS_ID" == "" ]; then
    return 2>/dev/null || exit
else
    echo Stopping process $PROCESS_ID
    kill $PROCESS_ID
    sleep 2
fi

# Try a second time
PROCESS_ID=`lsof -i :$PORT | grep java | awk '{print $2}'`
if [ "$PROCESS_ID" == "" ]; then
    return 2>/dev/null || exit
else
    echo Waiting for process $PROCESS_ID
    sleep 5
    echo Shutting down process $PROCESS_ID
    kill -9 $PROCESS_ID
fi

