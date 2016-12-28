# Funnel

[![Join the chat at https://gitter.im/oncue/funnel](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/oncue/funnel?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

[![Join the chat at https://gitter.im/oncue/quiver](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/oncue/funnel)
[![Build Status](https://travis-ci.org/oncue/funnel.svg)](https://travis-ci.org/oncue/funnel)
[ ![Download](https://api.bintray.com/packages/oncue/releases/funnel/images/download.svg) ](https://bintray.com/oncue/releases/funnel/_latestVersion)

A distributed monitoring system based on a lightweight streaming protocol. To get started, please read the [appropriate documentation.](http://oncue.github.io/funnel/)

## Versioning

Funnel operates an automated release system. Any merges to master produce a usable release provided the tests pass. The policy is as follows *<breaking>.<feature>.<patch>* where features are source compatible, and patches are binary compatible.

## Contributing

Contributions are welcome; please send a pull request or raise an issue as appropriate.

All development and test phase logging controls are configured from the `logback-test.xml` files the `etc` directory in the root of the project. Likewise, all development configuration files are checked into the `etc` folder to ensure that local development and testing configurations never accidentally make it into a certified release.

