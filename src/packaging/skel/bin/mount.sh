#!/usr/bin/env bash

exec /opt/${cmd.mount}/jre/bin/java -jar /opt/${cmd.mount}/app/${cmd.mount}.jar "$@"