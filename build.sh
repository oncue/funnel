#!/bin/bash
set -ev
# the environment variable ${TRAVIS_PULL_REQUEST} is set
# to "false" when the build is for a normal branch commit.
# When the build is for a pull request, it will contain the
# pull request’s number.
if [ "${TRAVIS_PULL_REQUEST}" = "false" ]; then
  sbt "release cross with-defaults"
else
  # for pull requests only do tests
  sbt ++$TRAVIS_SCALA_VERSION +test
fi
