---
layout: default
title:  "Modules"
section: "modules"
---

# {{ page.title }}

*Funnel* comes with a range of modules that allows composistion of the system in a variety of ways. Some modules are straight exporters, others provide both importing and exporting functionality.

<a name="elastic-flattened"></a>

## Elastic Search (Flattened)

Primarily for visualisation, this consumer takes the stream and aggregates into one document *per-metric* per time window, and then sends those documents to Elastic Search as a single `PUT` operation. The format of the message looks like this:

```
{
  "@timestamp": "2015-11-06T03:29:18.779+0000",
  "window": "now",
  "name": "jvm.memory.total.used",
  "host": "ip-10-124-10-140",
  "uri": "ip-10-124-10-140",
  "cluster": "flask-5.0.450",
  "flask": "ip-10-124-10-140",
  "units": "megabytes",
  "environment": "imdev",
  "stack": "flask-5.0.450",
  "numeric": {
    "count": 1,
    "variance": 0,
    "kurtosis": null,
    "mean": 4662.508824,
    "last": 4662.508824,
    "skewness": null,
    "standardDeviation": 0
  },
  "kind": "numeric",
  "type": "stats"
}
```

Using this document format enables extreamly fast indexing of documents in Elastic Search and gives great performance when using Kibana 4 (or later) for data visulisation.

<a name="elastic-exploded"></a>

## Elastic Search (Exploded)

**This sink is only to be used for light traffic, and at high-load it will cause yuor elastic search cluster to bloat its field space beyond the recomended safe limit. For high-volume workloads use the Flattened ES sink.**

Primarily for visualisation, this consumer takes the stream and aggregates into one document *per-funnel-host* per time window, and then sends those documents to Elastic Search in a bulk `PUT` operation. The format of the message looks like this:

```
{
  "@timestamp": "2014-08-22T17:37:54.201855",
  "cluster": "imqa-maestro-1-0-279-F6Euts",  #This allows for a Kibana search, cluster: x
  "host": "ec2-107-22-118-178.compute-1.amazonaws.com",
  "previous" {
    "jvm": {
      "memory": {
        "heap": {
          "committed": {
            "last": 250.99763712000001,
            "mean": 250.99763712000001,
            "standard_deviation": 0.0
          },
          "usage": {
            "last": 0.042628084023299997,
            "mean": 0.042445506024100001,
            "standard_deviation": 0.00018257799924300001
          }
        }
      }
    }
  }
}
```

Using this document format enables Elastic Search to reason about both aggregate time queries, and top-n queries - the two primary use cases for monitoring visualisation.

<a name="nginx"></a>

## Nginx

*Funnel* has a module for importing metrics from a specified Nginx web server. Provided your Nginx setup has the [stub_status](http://nginx.org/en/docs/http/ngx_http_stub_status_module.html) module installed and setup, *Funnel* can import a set of information about current nginx connections, and lifetime behaviour of that Nginx process.

The funnel agent has a convenient usage of this module, but users can also bundle this inside their application if they wish to collect Nginx statistics from an on-board JVM process directly. Usage is exceedingly simple:

```
package yourapp

import java.net.URI

object Main {
  def main(args: Array[String]): Unit = {
    ...
    val uri = new URI("http://localhost:4444/stub_status")
    funnel.nginx.Import.periodically(uri)
    ...
  }
}

```

<a name="zeromq"></a>

## ZeroMQ

The [ZeroMQ](http://zeromq.org/) module provides both the ability to publish metrics from a `Monitoring` instance out to a socket (with an arbitrary transport and mode), and the ability to consume metrics from a socket and import them into the specified `Monitoring` instance.

#### Installation

The ZeroMQ module comes in two flavours:

1. `zeromq` which uses the *native* `libzmq` C++ library and allows use of UNIX domain sockets, PGM etc. This is the reference implementation of ZMTP.

1. `zeromq-java` which uses the pure-Java implementation of the same library API. This module is meant purely for compatiblity with Windows systems, as it cannot leverage domain sockets or PGM. Do not use this module unless you are sure of what you're doing.

With this frame, in order to run locally you'll need to install the `libzmq` and the `jzmq` native binaries. You can do that by running the following steps:

1. Install ZeroMQ. Be sure to install ZeroMQ 4.0.+ (4.1 is NOT compatible)

```
brew update && brew install zeromq
```

2. Build the JZMQ binaries
3. Unzip the file and execute the following shell

```
cd jzmq-3.1.0 && sudo mv libjzmq* /Library/Java/Extensions/

```

That's all there is to it!

#### Exporting

In order to publish your metrics using ZeroMQ, ensure that you have the following in your `build.sbt`:

```
libraryDependencies += "oncue.funnel" %% "zeromq" % "x.x.x"
```

This will allow you to setup the publishing of metrics. Technically, this publishing happens on a background daemon thread, so its typically best to put this line at the very edge of your work (typically the application main):

```
package foo

object Foo {
  def main(args: Array[String]): Unit = {
    funnel.zeromq.Publish.toUnixSocket()
  }
}

```

**NOTE**: If you do not have the native library installed you will see an error at runtime on your local machine. For development purposes this can be ignored - the binaries are instaleld by default on the foundation images used during deployment.

If you would like more control over when the `Publish` process is started and stopped, then you can simply pass the function an async `Signal[Boolean]`:

```
package foo

import scalaz.stream.async.signalOf
import scalaz.stream.async.mutable.Signal
import scalaz.concurrent.Task
import funnel.zeromq.Publish

object Foo {

  def stop(signal: Signal[Boolean]): Task[Unit] = {
    for {
      _ <- signal.set(false)
      _ <- signal.close
    } yield ()
  }

  def main(args: Array[String]): Unit = {
  	 val alive = signalOf(true)
    Publish.toUnixSocket(signal = alive)

    // sometime later when you want to stop
    stop(alive)
  }
}

```

#### Importing

Similarly, importing metrics is trival. Please note this documentation is intended for completeness only and is not a typical user-level API unless one is implementing a *Flask*-like process. Given that `Monitoring.mirrorAll` simply requires a `DatapointParser` type, the ZeroMQ module supplies one like this:

```
package foo

import scalaz.stream.async.signalOf
import funnel.zeromq.Publish
import funnel.DatapointParser

object Foo {

  def main(args: Array[String]): Unit = {
  	 val alive = signalOf(true)
    val dp: DatapointParser = Mirror.from(alive) _
  }
}
```

#### Endpoints

When using the ZeroMQ module primitives to do anything with sockets, you will need to specify and `Endpoint`. The module provides a range of useful combinators to make creating the type of socket you want simple. Here's an example:

```
package foo

import scalaz.stream.async.signalOf
import funnel.zeromq.{Endpoint,sockets}, sockets._
import java.net.URI

object Foo {

  def main(args: Array[String]): Unit = {
  	 val uri = new URI("ipc:///tmp/foo.socket")
    val E1 = Endpoint(publish &&& bind, uri)
    val E2 = Endpoint(push &&& bind, uri)
    val E3 = Endpoint(subscribe &&& (connect ~ topics.all))
  }
}
```

Essentially the ZeroMQ socket types are modelled as functions and you can specify if you want to be the side of the connection that `bind`'s or `connect`'s. In the case of PUB-SUB you also have the option to specify a discriminating predicate for topic subscription.

#### Wire Protocol

Whilst the 0mq socket uses [ZMTP](http://zmtp.org/), the payload framing is always the responsibility of the implementing application. Subsequently, *Funnel* has an application-layer scheme for handling versioning and message discrimination on the wire. *Funnel* uses a single header frame followed by all subsequent payload frames. The header frame acts like a routing envelope for the message payload, and it has a simple delimited text construction that looks like this:

```
<scheme>/<version>/<window>/<topic>

```
Some of the fields here are *optional*, and but all fields have an important role to play:

* `scheme`: mandatory element that represents the types of payload that this message (set of frames) contains. At the time of writing the *Funnel* module supported two types of message scheme: `fsm` and `telem`, everything is classified as `unknown`.

* `version`: mandatory element that represents the version of the prefixed `scheme`. This is vital for protocol evolution over time, and clients are expected to parse the version information and be able to support multiple scheme versions simultaneously. Often the combination of `scheme` and `version` is everything the client needs to figure out how to handle a particular payload.

* `window`: *optional* element that denotes the *Funnel* window period this message pertains too. At the time of writing this was really only applicable to the `fsm` scheme, and only makes sense when *Flask* is consuming metrics from a remote target.

* `topic`: *optional* element that denotes any further specification about which metric key this payload is related too. At the time of writing this was really only applicable to the `fsm` scheme, and only makes sense when *Flask* is consuming metrics from a remote target.


