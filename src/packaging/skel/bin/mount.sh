#!/usr/bin/env bash

exec /opt/${cmd.mount}/jre/bin/java --enable-preview -jar /opt/${cmd.mount}/app/${cmd.mount}-${project.version}.jar "$@"
