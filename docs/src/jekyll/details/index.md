---
layout: default
title:  "Details"
section: "details"
---

# Detailed Information

As funnel is a fairly comprehensive system, but its user-level API is relatively constrained, this page adds some additional detail to both some of the internal workings and useful things to know as users.

1. [Built-in Metrics](#builtin-metrics)
1. [Control Server](#control-server)

<a name="builtin-metrics"></a>

# Built-in Metrics

Using the default instruments, you'll notice that there's a raft of metrics being produced automatically. These logically fall into two categories:

1. [JVM and Clock Metrics](#jvm-metrics) - list of the built-in JVM metrics
1. [System Metrics](#system-metrics) - information about the built-in system metrics 

The following subsections cover these in more detail.

<a name="jvm-metrics"></a>

## JVM and Clock Metrics

The default set of instruments (`funnel.instruments`) will automatically collect the following JVM statistics (where `:period` is one of either `now`, `sliding` or `previous`):

* `:period/jvm/memory/nonheap/init`
* `:period/jvm/memory/heap/committed`
* `:period/jvm/memory/total/committed`
* `:period/jvm/gc/ParNew`
* `:period/jvm/memory/nonheap/committed`
* `:period/jvm/gc/ConcurrentMarkSweep`
* `:period/jvm/gc/ConcurrentMarkSweep/time`
* `:period/jvm/memory/nonheap/max`
* `:period/jvm/memory/heap/usage`
* `:period/jvm/gc/ParNew/time`
* `:period/jvm/memory/heap/init`
* `:period/jvm/memory/nonheap/used`
* `:period/jvm/memory/total/max`
* `:period/jvm/memory/heap/used`
* `:period/jvm/memory/total/init`
* `:period/jvm/memory/total/used`
* `:period/jvm/memory/heap/max`
* `:period/jvm/memory/nonheap/usage`

In addition to these JVM metrics, the default instruments also provide the following:

* `:period/elapsed` - amount of time that has elapsed so far in the specified period.
* `:period/remaining` - amount of time remaining in the specified period.
* `uptime` - Amount of time that this monitoring instance has been running.

These are keys provided by the default instruments, but if you would like to create a custom `Instruments` instance, this is entirely possible. The only reason you would typically want to do this is to specify a custom boundary for time periods. **This is not generally advisable** as the operation logic expects the time period defined by the library. 

````
import funnel._
import scala.concurrent.duration._

val I = new Instruments(2 minutes, Monitoring.default) with DefaultKeys {
  JVM.instrument(this)
  Clocks.instrument(this)
}
````

<a name="system-metrics"></a>

## System metrics

The default set of instruments (`funnel.instruments`) will also automatically collect operating system statistics if the Hyperic SIGAR library is available on the `java.library.path` (by default on Linux systems this maps to the `LD_LIBRARY_PATH` environment variable).

Download the library here: [http://sourceforge.net/projects/sigar/files/](http://sourceforge.net/projects/sigar/files/).

SIGAR gathers a large set of metrics which will appear in Funnel under `:period/system`:

* `:period/system/cpu/aggregate` - Aggregate CPU metrics.
* `:period/system/cpu/:n` - Per-CPU metrics for individual CPU number `:n`.
* `:period/system/tcp` - TCP/IP statistics.
* `:period/system/file_system/:fs` Filesystem statistics where `:fs` is an individual filesystem mount point. The mount point name is URL-encoded.
* `:period/system/mem` - Memory statistics.
* `:period/system/load_average` - Load averages for 5, 10, and 15 minute intervals.
* `system/uptime` - Amount of time the OS has been running.

<a name="control-server"></a>

# Control Server

Funnel comes with a built in system for plug-able monitoring servers - that is, a way to expose the internal streams over some kind of wire protocol. At the time of writing, there was only a single `MonitoringServer` implementation that uses `HTTP` as its wire protocol and `JSON` over `SSE` as its serialisation strategy. This is entirely flexible, and if one wanted to implement a different `MonitoringServer` it would be entirely easy to do so externally by writing a `scalaz.stream.Process[Task, A]` consumer. 

### Bootup

The working expectation is that applications that expose metrics of their own (or indeed, consume metrics from others they wish to replicate) expose a monitoring server. This is done simply by calling `MonitoringServer.start`

````
import funnel.http.{MonitoringServer,Monitoring}

object Main {
  def def main(args: Array[String]): Unit = {
    MonitoringServer.start(Monitoring.default, 5775)

    // your application code starts here 
  }
}

````

Once the server is booted as a part of your application, you can visit the specified port on `localhost` and have access to a range of resources. 

### Resources

* `/:period` - For example, `/now`, `/sliding` or `/previous`. Displays all of the metric keys that have values in that period. If there is nothing to display, the resulting JSON array will be empty. 

* `/:period>/:metric-prefix` - For example, `/now/uptime` or `/now/http/get/index`. The key thing to understand here is that `:metric-prefix` here really means the name of the key (or at least the first part of it), which is why its so useful to bucket keys by the common prefix: you can lookup all the keys related to a given subject. For example, `/now/http` gets you all the metric keys that start with the string `http`.

* `/stream` - obtain a stream of everything that is happening in the system right now as a HTTP SSE stream.


### JSON

````
{
  "key": {
    "name": "now/jvm/memory/nonheap/init",
    "type": "Stats",
    "units": "Megabytes"
  },
  "value": {
    "count": 9,
    "variance": 58.38652776185048,
    "kurtosis": 4.1249999999999964,
    "mean": 21.61231644444444,
    "last": 24.313855999999998,
    "skewness": -2.474873734152916,
    "standardDeviation": 7.6411077575081015
  }
}
````

### Queries

For any given resource that serves metrics or metric keys, you can restrict the keys that are returned by specifying them in the query parameters of the URL. For example, to request only keys from the `/stream/now` resource whose type is `String` use a URL like the following:

```
http://localhost:5777/stream/now?type="String"
```

The quotes around `"String"` are important since the query value must actually be valid JSON. You can also specify a value for `units` if you want, for example, all the keys whose values are measured in megabytes.

When querying a Flask instance, some keys will be associated with buckets and urls ([see the Flask documentation]({{ site.baseurl }}getting-started/#flask)). You can request keys from a specific bucket or URL by specifying `bucket="myBucket"` or `url="myURL"`, as desired.

