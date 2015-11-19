---
layout: default
title:  "Frequently Asked Questions"
section: "getting-started"
---

# {{ page.title }}

*Funnel* is a complex system, and whilst we - the makers - have tried to make it as simple to use as possible, there are still some things we cannot insulate users from, which usually stem from important choices users must make in order to get the best out of the system. Subsequently, this FAQ serves as a reference for these nuances.

<a name="instrument-count"></a>

### Counts with statistical instruments

Most of the numerical values *Funnel* outputs have an internal type of `Stats`. This essentially represents the [five statistical moments](https://en.wikipedia.org/wiki/Moment_(mathematics)), with the addition of a `count` field. Often users assume the `count` field is the number of times a particular *timer* or *numericGauge* is updated. This however is not accurate: `count` represents the number of samples funnel has made to this instrument in the given window. *Funnel* instruments are buffered to prevent flooding our backend collection system, and as such, we do not sample instruments at full resolution, subsequently, the `count` field represents the number of samples that were actually taken. 

If the user does desire an atomic count of instrument usage, it is recommended to use a *counter* in addition to the stats-producing instrument. However, as this is a very common use-case, *Funnel* provides the `lapTimer` instrument to obtain both a `counter` and a `timer` with the same name.

<a name="infrequent-updates"></a>

### Seemingly inaccurate reporting for infrequent updates

In high-load systems, *Funnel* samples updates to avoid flooding the backend collection infrastructure. Whilst this is a massive benefit for high-load systems, for batch or infrequently updated instruments, one has to be a bit careful about how values in Elastic Search are interpreted. Specifically, using the `max` function offered by Elastic Search on a `mean` stats field that is infrequently updated will often provide skewed results. The reason for this is that the `mean` that is computed on the "leaf" machines (e.g. your foo service) and if there have been very few samples taken within a given window, then the `mean` is subsequently skewed. In this case, to get a more realistic average value, one should use the `avg` (Average) aggregation function offered by Elastic Search, and apply that to the `mean` field of the stats instrument. This should then linearise the aforementioned skewing. 

<a name="numeric-gauges"></a>

### Use of gauge vs numericGauge

There is a subtle nuance between `gauge` and `numericGauge` instruments. Specifically, `gauge` can be used with any `A` that is `Reportable`. Whilst this is useful for some causes, the majority of gauges you would need form some kind of `numericGauge` (i.e. their value is usually `Double`, `Int`, etc), and as such its very important that you use `numericGauge` and not `gauge` for these values. The subtle difference here is that because `gauge` is the general-case instrument, its input cannot be guaranteed to aggregate (form a `Group`), so the metric values are only outputted on the `now` stream, which is not typically collected by *Flask*, as the `now` stream has a huge amount of throughput compared to `previous` (by default *Chemist* is configured to only pull `String` items from the `now` - strings do not aggregate but form traffic lights and edges - so your plain `gauge` would not be collected). `numericGauges` on the other hand output in all three streams: `now`, `sliding` and `previous`.



