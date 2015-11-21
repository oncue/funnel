#!/bin/bash
set -ev
if [ "${TRAVIS_PULL_REQUEST}" = "false" ]; then
  # for pull requests only do tests
  sbt ++$TRAVIS_SCALA_VERSION +test
else
  # for merges, do releases
  mkdir ~/.bintray/
  FILE=$HOME/.bintray/.credentials
  cat <<EOF >$FILE
realm = Bintray API Realm
host = api.bintray.com
user = $BINTRAY_USER
password = $BINTRAY_API_KEY
EOF
  echo $BINTRAY_USER
  echo "Created ~/.bintray/.credentials file"
  sbt "release cross with-defaults"
fi
