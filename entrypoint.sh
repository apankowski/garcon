#!/bin/sh

exec java $JAVA_OPTS $APP_OPTS \
  -Djava.security.egd=file:/dev/./urandom \
  -Dfile.encoding=UTF-8 \
  -Dorg.jooq.no-logo=true \
  -Dorg.jooq.no-tips=true \
  -jar application.jar
exit $?
