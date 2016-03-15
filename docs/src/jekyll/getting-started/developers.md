---
layout: default
title:  "Getting Started - Developers"
section: "getting-started"
---

# Getting Started: Developers

First you need to add the dependency for the monitoring library to your `build.scala` or your `build.sbt` file:

``` scala
libraryDependencies += "oncue.funnel" %% "http" % "x.y.z"
```

(check for the latest release by [looking on bintray](https://bintray.com/oncue/releases/funnel/view))

Current transitive dependencies introduced on your classpath:

- [Scalaz](https://github.com/scalaz/scalaz) 7.1.1
- [Scalaz Stream](https://github.com/scalaz/scalaz-stream) 0.7.3a
- [Algebird Core](https://github.com/twitter/algebird) 0.8.0

You will likely never touch these dependencies directly, but its important to be aware of them in case you decide yourself to use Scalaz (for example); if you have conflicting versions then you will see some very strange binary collisions at runtime.

### Defining Metrics

With the dependency setup complete, you can now start instrumenting your code. The first thing you need to do is create a `metrics.scala` file, which should look something like this:

``` scala
package yourapp

import funnel.instruments._

object metrics {
  val HttpReadWidgets = timer("http/get/widgets",
    "time taken to read and display the list of widgets")
  val HttpReadWidget  = timer("http/get/widgets/id",
    "time taken to read and display a given widget")
  val FooCount        = counter("domain/foocount",
    "the number of foos that have been seen")
  // more metrics here
}
```

The goal of this `metrics` object is to define your metrics up front, and force yourself to think about the metric requirements that your application has. All of your application metrics should be defined in the `metrics` object, and imported where they are needed by specific APIs.

> **NOTE:** When it comes to naming metrics, always bucket your metrics using `/` as a delimiter, and try to logically order things as a "tree". For example:
>
> * When implementing timers for HTTP resources, use the idiom: `http/<verb>/<resource>`. If parts of the resource are variable, then use `:yourvar`, or whatever parameter name makes most sense. The reason for structure HTTP metrics like this is that it is easily consumable by operations and that it makes it easy to quickly compare different resources for a given HTTP verb (for example)
> * As a rule of thumb, try to logically order your metrics in order of component coarseness. For example, anything starting with "db" for database operations can all be compared together in a convenient fashion, so it would make sense to bucket all database operations together, and then perhaps bucket all the *read* operations together, versus the *write* operations (see below for a more complete example)

At first blush, clearly having a single namespace for the entire world of application metrics could become unwieldy, so it is recommended to organise your metrics with nested objects, based on logical application layering. Here's a more complete example from the SU service that illustrates this pattern:

``` scala
package yourapp

import funnel.instruments._

object metrics {

  /**
    * Instruments for everything related to the HTTP stack.
    */
  object http {
    val ReadUpdates       = timer("http/post/updates",
      "time taken to determine if the supplied device profile needs any software updated")
    val CreateAllocations = timer("http/post/allocations",
      "time taken to make new allocations")
    val ReadAllocations   = timer("http/get/allocations/key",
      "time taken to read a specified allocation")
    val DeleteAllocation  = timer("http/delete/allocations/key",
      "time taken to delete an allocation")
    val ReadGroups        = timer("http/get/groups",
      "time taken to load and present all defined groups as json")
    val CreateGroup       = timer("http/post/groups",
      "time taken to add a new group")
    val ReadGroup         = timer("http/get/groups/key",
      "time taken to read a group definition and display as json")
    val UpdateGroup       = timer("http/put/groups/key",
      "time taken to replace the definition of a group")
    val DeleteGroup       = timer("http/delete/groups/key",
      "time taken to delete a group with a given key")
  }

  /**
    * Instruments for the database access operations
    */
  object db {
    val ReadLatency       = timer("db/read/single/latency",
      "time taken to read a single entry from the database")
    val ReadBatchLatency  = timer("db/read/batch/latency",
      "time taken to read a batch of items from the database with a specified set of keys")
    val ReadListLatency   = timer("db/read/list/latency",
      "time taken to list items from the database with a given hash+range combination")
    val ReadScanLatency   = timer("db/read/scan/latency",
      "time taken to scan the specified database table")
    val WriteLatency      = timer("db/write/single/latency",
      "time taken to write a single entry into the database")
    val WriteBatchLatency = timer("db/write/batch/latency",
      "time taken to write a batch to the database")
    val DeleteLatency     = timer("db/delete/batch/latency",
      "time taken to delete a batch from the database")
  }
}
```

The 1.0.x series of `funnel-core` has five  supported metric types: `Counter`, `Timer`, `Gauge`, `TrafficLight`, and `Edge`.


#### Counters

A counter simply increments an integer value specified by a label that you supply. A counter is appropriate for metrics that represent a tally, or the number of occurrences of something during a particular period. Typical examples are the number of sales, the number of errors, or messages received.

You should **not** use a counter for a magnitude or amount that represents the current value of something, such as the number of concurrent connections, the number of ongoing transactions, or the number of messages in a queue. In that case, use a _gauge_ instead (see below).

For example, to create a counter for the number of cache hits:

``` scala
package yourapp

import funnel.instruments._

object metrics {
  val NumberOfCacheHits = counter("cache/hits")
}
```

Counters can then be incremented monotonically, or incremented by a given amount:

``` scala
import metrics._

trait SomeFun {

  def foobar = {
    // increment monotonically
    NumberOfCacheHits.increment

    // increment by a given amount
    NumberOfCacheHits.incrementBy(10)
  }

}
```

#### Timers

As the name suggests, **timers** are all about the amount of time take to perform a specific operation.

``` scala
package yourapp

import funnel.instruments._

object metrics {
  val GetUserList = timer("db/get-user-list")
}
```

Timers can then record several different types of value:

``` scala
import metrics._
import scalaz.concurrent.Task
import scala.concurrent.Future

trait SomeFun {

  private def getList: Future[Int] = …
  private def getListTask: Task[Int]

  def foobar = {

    // for timing strict values, just use the block syntax:
    GetUserList.time {
      // expensive operation goes here
    }

    // for timing async Future[A]
    GetUserList.timeFuture(getList)

    // for timing scalaz.concurrent.Task[A]
    GetUserList.timeTask(getListTask)

    // for anything else procedural, the side-effecting API
    val stopwatch = GetUserList.start()
    val x = // operation to be timed
    stopwatch() // stops the stopwatch and records the time taken

  }

}
```

#### Gauges

A gauge represents the current state of a value. A good analogy is checking the oil on a car; the dipstick is the gauge of how much oil the engine currently has, and this changes over time, moving both up and down, but it can only have a single value at any particular reading.

Use a gauge when you want to monitor the magnitude or amount of something at a particular point in time, or the average amount of something over a period, such as the number of concurrent connections, ongoing transactions, or messages in a queue. A gauge is **not** appropriate for counting or tallying occurrences of some event, such as the number of errors, number of sales, or messages received so far. For such things, use a _counter_ instead (see above).


``` scala
package yourapp

import funnel.Units
import funnel.instruments._

object metrics {
  // gauges require an initial value
  val OilGauge = gauge("oil", 0d, Units.Count)
}
```

Unlike other metrics, gauges are strongly typed in their value, and must be given a default upon being created. To set the value later on in your code, you simply do the following:

``` scala
import metrics._

trait SomeFun {
  def readOilLevel: Double = {
    val level = // database op
    OilLevel.set(level)
    level
  }
}
```

By default, the following types are supported as values for gauges:

* `String`
* Any `Numeric[A]` instance; the Scala std library supplies typeclasses for:
  * `Int`
  * `Double`
  * `Float`
  * `BigDecimal`
  * `Long`

In addition to simply recording the value of the gauge, there is a specialised gauge that can also track numerical statistics like `mean`, `variance` etc. To use it, just adjust your `metrics` declaration like so:

``` scala
package yourapp

import funnel.instruments._

object metrics {
  // gauges require an initial value
  val OilGauge = numericGauge("oil", 0d, Units.Count)
}
```

There is a subtle nuance between `gauge` and `numericGauge` instruments. Specifically, `gauge` can be used with any `A` that is `Reportable`. Whilst this is useful for some causes, the majority of gauges you would need form some kind of `numericGauge` (i.e. their value is usually `Double`, `Int`, etc), and as such its very important that you use `numericGauge` and not `gauge` for these values. The subtle difference here is that because `gauge` is the general-case instrument, its input cannot be guaranteed to aggregate (form a `Group`), so the metric values are only outputted on the `now` stream, which is not typically collected by *Flask*, as the `now` stream has a huge amount of throughput compared to `previous` (by default *Chemist* is configured to only pull `String` items from the `now` - strings do not aggregate but form traffic lights and edges - so your plain `gauge` would not be collected). `numericGauges` on the other hand output in all three streams: `now`, `sliding` and `previous`.

#### Traffic Light

As an extension to the concept of `Gauge`, there is also the notion of a "traffic light" which is essentially a gauge that can be in only one of three possible finite states:

* Red
* Amber
* Green

Traffic light metrics are used to derive actionable signals for operations (they can derive whatever they like too), but for example, one might put a traffic light metric on database access, or the status of a circuit breaker used to invoke a 3rd party system. Using traffic lights is super simple:

``` scala
package yourapp

import funnel.instruments._

object metrics {
  val GeoLocationCircuit = trafficLight("circuit/geo")
}
```

And the actual usage in the circuit breaking code:

``` scala
import metrics._

trait SomeFun {
  def doGeoLocation = {
    // your logic for the function
    …
  // if all went well, set the breaker accordingly
   GeoLocationCircuit.green

   /*
   Traffic lights can be set into red and amber states in a similar fashion:
   GeoLocationCircuit.amber
   GeoLocationCircuit.red
   */
  }
}
```

#### Edge

An `Edge` instrument provides inter-service telemetry. Its purpose is to
monitor the status and timings of service-to-service connections. It's just
an aggregation of four other instruments:

* `origin`: A continuous string gauge
* `destination`: Another continuous string gauge
* `timer`: A timer
* `status`: A traffic light

The `origin` is the actual origin of the connection. The `destination` is
where the connection is being made to. The `timer` is a measure of some
latency or roundtrip timing between the origin and destination, and the
`status` is some notion of the overall status of the connection
(Red, Amber, or Green).

In a typical use case, we're making a connection from one service to
another, relying on some discovery service or load balancer to provide us
with an actual host to connect to. The `origin` is the local hostname,
and the `destination` should be the actual remote hostname. Each time we are
redirected to a new host for the service, we should set the `destination`
to the name of that host. When we make a request across the connection, we
should time it with the edge `timer`. If we detect that the remote host is
down, we should set the `status` traffic light to `Red`. In a connection
configured with a circuit-breaker, we can use `Amber` to indicate the
"half-open" state.

Edges are created with:

``` scala
import funnel.instruments._

val anEdge = edge("myservice/otherservice",
                  "The edge from my service to some other service",
                  "10.0.0.1",
                  "10.0.0.2")
```


#### LapTimer

A `LapTimer` instrument is a compound instrument, which combines Timer and Counter instruments. It adds counter to all the timer operations.

A Timer can be easily replaced with LapTimer, by simply replacing the way it is created:

``` scala
package oncue.svc
package yourproject

import funnel.instruments._

object metrics {
  val GetUserList = lapTimer("db/get-user-list")
}
```

LapTimer fully supports Timer features:

``` scala
import metrics._
import scalaz.concurrent.Task
import scala.concurrent.Future

trait SomeFun {

  private def getList: Future[Int] = …
  private def getListTask: Task[Int]

  def foobar = {

    // for timing strict values, just use the block syntax:
    GetUserList.time {
      // expensive operation goes here
    }

    // for timing async Future[A]
    GetUserList.timeFuture(getList)

    // for timing scalaz.concurrent.Task[A]
    GetUserList.timeTask(getListTask)

    // for anything else procedural, the side-effecting API
    val stopwatch = GetUserList.start()
    val x = // operation to be timed
    stopwatch() // stops the stopwatch and records the time taken

  }

}
```

### Derived Metrics

Funnel provides an API for combining metrics via the `funnel.Metric` datatype. For example, to make a metric that tracks a ratio between the present value of two timers:

``` scala
import funnel.instruments._
import funnel.Metric._
import scalaz.syntax.applicative._

val timer1 = timer("t1")
val timer2 = timer("t2")

val ratio: Metric[Double] =
  (timer1.keys |@| timer2.keys)(_ + _)

// do this only once at the endge of the world:
val _ = ratio.publish("foo/mytimer", Units.Milliseconds).run

```

We could publish a metric that determines whether our application is "healthy", meaning that we haven't received more than 20,000 requests in the current window, the database is up, and our average query response time is under 20 milliseconds.

``` scala

val reqs = counter("requests")
val dbOk = gauge("db-up", true)
val query = timer("query-speed")

val healthy: Metric[Boolean] =
  (reqs.keys.now |@| dbOk.keys |@| query.keys) { (n, db, t) =>
     n < 20000 &&
     db &&
     t.mean < 20
  }

// do this only once at the endge of the world:
healthy.publish("healthy", Units.Healthy).run

```

### Monitoring Server

All the metrics you define in your application, plus some additional platform metrics supplied by the monitoring library can be exposed via a baked in HTTP server. In order to use this server, one simply only needs to add the following line to the `main` of their application:

``` scala
import funnel.http.MonitoringServer
import funnel.Monitoring

object Main {
  def main(args: Array[String]): Unit = {
    MonitoringServer.start(Monitoring.default, 5775)

    // your application code starts here
  }
}
```

**NOTE: By application `main`, this does not have to be the actual main, but rather, the end of the world for your application (which however, would usually be the main). For Play! applications, this means the Global object.**

With this in your application, and assuming you are developing locally, once running you will be able to access [http://127.0.0.1:5775/](http://127.0.0.1:5775/) in your local web browser, where the index page will give you a list of available resources and descriptions of their function.

