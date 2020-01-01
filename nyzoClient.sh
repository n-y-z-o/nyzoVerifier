CONFIGURATION_FILE_PATH=./nyzoClient.conf
VERIFIER_ROOT=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

sudo rm $CONFIGURATION_FILE_PATH 1>/dev/null 2>/dev/null

cat << END_OF_FILE >> $CONFIGURATION_FILE_PATH
[program:nyzo_client]
command=/usr/bin/java -jar -Xmx3G $VERIFIER_ROOT/build/libs/nyzoVerifier-1.0.jar co.nyzo.verifier.client.Client
autostart=true
autorestart=true
startsecs=10
startretries=20
stdout_logfile=/var/log/nyzo-client.log
stdout_logfile_maxbytes=10MB
stdout_logfile_backups=2
redirect_stderr=true
END_OF_FILE
