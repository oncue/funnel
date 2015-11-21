#!/bin/bash
set -ev
if [ "${TRAVIS_PULL_REQUEST}" = "false" ]; then
  # for pull requests only do tests
  sbt ++$TRAVIS_SCALA_VERSION +test
else
  sbt "release cross with-defaults"
fi
