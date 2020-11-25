#!/bin/sh

# For Heroku: https://devcenter.heroku.com/articles/heroku-postgresql#connecting-in-java
# Unfortunately we have to convert the Postgres-style conenction string to JDBC form as Heroku
# does this automatically only for Java buildpacks, not free-form container deployments as ours.
if [ -n "$DATABASE_URL" ]; then
 export JDBC_DATABASE_URL=`echo $DATABASE_URL | sed -nr 's_postgres://.*@(.*)_jdbc:postgresql://\1_p'`
 export JDBC_DATABASE_USERNAME=`echo $DATABASE_URL | sed -nr 's_.*//([a-zA-Z0-9]+):([a-zA-Z0-9]+)@.*_\1_p'`
 export JDBC_DATABASE_PASSWORD=`echo $DATABASE_URL | sed -nr 's_.*//([a-zA-Z0-9]+):([a-zA-Z0-9]+)@.*_\2_p'`
fi

exec java $JAVA_OPTS $APP_OPTS \
  -Djava.security.egd=file:/dev/./urandom \
  -Dorg.jooq.no-logo=true \
  -jar application.jar
exit $?
