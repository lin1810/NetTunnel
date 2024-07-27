#!/usr/bin/env bash
#===============================================================================
#          FILE: entrypoint.sh
#
#         USAGE: ./entrypoint.sh
#
#   DESCRIPTION: Entrypoint for NetTunnel docker container
#
#       OPTIONS: ---
#  REQUIREMENTS: ---
#          BUGS: ---
#         NOTES: ---
#        AUTHOR: lyndon shi (lyndon_shi@outlook.com),
#  ORGANIZATION:
#       CREATED: 2023/05/12 14:11
#      REVISION: 1.0
#===============================================================================

set -o nounset                              # Treat unset variables as an error

### usage: Help
# Arguments:
#   none)
# Return: Help text
usage() { local RC="${1:-0}"
    echo "Usage: ${0##*/} [-opt] [command]
Options (fields in '[]' are optional, '<>' are required):
    -h          This help
    -i \"<name;port>[;slidingWindowSize][;allList]\"
                Configure a tunnel
                required arg: \"<name>;<port>\"
                <name> tunnel name
                <port> tunnel bind port
                NOTE: for the default value, just leave blank
                [slidingWindowSize] default:'10'
                [all IP Region List split by delim char '|']
" >&2
    exit $RC
}

### addInstance: Add Tunnel Instance
# Arguments:
#   name) instance name
#   port) tunnel bind port
#   slidingWindowSize) Integer, default:'10'
# Return: result
addInstance() { local name="$1" port="$2" slidingWindowSize="${3:-""}" allowList="${4:-""}"  \
                file=$CONFIG_PATH/application-instance.yml
    echo "      - instance-name: $name" >>$file
    echo "        port: $port" >>$file
    if [ -n "$slidingWindowSize" ]; then
        echo "        sliding-window-size: $slidingWindowSize" >>$file
    fi
    if [ -n "$allowList" ]; then
            echo "        access-ip-region: $allowList" >>$file
        fi
    echo "" >>$file
}

if [ ! -f $CONFIG_PATH/application.yml ]; then
     cp application.yml $CONFIG_PATH/
fi

if [ $# -eq 0 ]; then
     usage 3
fi

cp application-instance.yml $CONFIG_PATH/

while getopts ":hi:" opt; do
    case "$opt" in
        h) usage ;;
        i) eval addInstance $(sed 's/^/"/; s/$/"/; s/;/" "/g' <<< $OPTARG) ;;
        "?") echo "Unknown option: -$OPTARG"; usage 1 ;;
        ":") echo "No argument value for option: -$OPTARG"; usage 2 ;;
    esac
done
shift $(( OPTIND - 1 ))

if [[ $# -ge 1 ]]; then
    echo "ERROR: command not found: $@"
    exit 13
else
    bash $BASE_PATH/generateCerts.sh
    java $JAVA_OPTS -Dspring.config.location=${CONFIG_PATH}/ -Dspring.profiles.active=instance -jar $BASE_PATH/app.jar
fi