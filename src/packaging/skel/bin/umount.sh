#!/usr/bin/env bash

KILLSIG=" "
while getopts ":f" opt; do
	case ${opt} in
		f ) # force kill
			KILLSIG=-9
			;;
		\? ) echo "Usage: ${BASH_SOURCE##*/} [-f]" && exit 1
			;;
	esac
done

MOUNTED=$(/opt/${cmd.mount}/jre/bin/jps | grep ${cmd.mount}-${project.version}.jar | cut -d ' ' -f 1)
[ -z "$MOUNTED" ] || kill $KILLSIG "$MOUNTED"
