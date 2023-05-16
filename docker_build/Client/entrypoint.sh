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
    -i \"<name;port>[;slidingWindowSize]\"
                Configure a tunnel
                required arg: \"<name>;<port>\"
                <name> tunnel name
                <port> tunnel bind port
                NOTE: for the default value, just leave blank
                [slidingWindowSize] default:'10'
" >&2
    exit $RC
}

### addInstance: Add Tunnel Instance
# Arguments:
#   name) instance name
#   address) tunnel address
#   slidingWindowSize) Integer, default:'10'
# Return: result
addInstance() { local name="$1" address="$2" slidingWindowSize="${3:-""}"  \
                file=$CONFIG_PATH/application-instance.yml
    echo "      - instance-name: $name" >>$file
    echo "        address: $address" >>$file
    if [ -n "$slidingWindowSize" ]; then
        echo "        sliding-window-size: $slidingWindowSize" >>$file
    fi
    echo "" >>$file
}

### serverAddress: set server address
# Arguments:
#   address) server address
# Return: result
serverAddress() { local address="$1" file=$CONFIG_PATH/application-instance.yml
    export SERVER_ADDRESS=$address
}

if [ ! -f $CONFIG_PATH/application.yml ]; then
     cp application.yml $CONFIG_PATH/
fi

if [ $# -eq 0 ]; then
     usage 3
fi

cp application-instance.yml $CONFIG_PATH/

while getopts ":hi:s:" opt; do
    case "$opt" in
        h) usage ;;
        i) eval addInstance $(sed 's/^/"/; s/$/"/; s/;/" "/g' <<< $OPTARG) ;;
        s) serverAddress $OPTARG ;;
        "?") echo "Unknown option: -$OPTARG"; usage 1 ;;
        ":") echo "No argument value for option: -$OPTARG"; usage 2 ;;
    esac
done
shift $(( OPTIND - 1 ))

if [[ $# -ge 1 ]]; then
    echo "ERROR: command not found: $@"
    exit 13
else
    java $JAVA_OPTS -Dspring.config.location=${CONFIG_PATH}/ -Dspring.profiles.active=instance -jar $BASE_PATH/app.jar
fi