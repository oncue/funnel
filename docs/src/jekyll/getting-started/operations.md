---
layout: default
title:  "Getting Started - Operations"
section: "getting-started"
---

# Getting Started: Operations

The *Funnel* monitoring system is an umbrella for several components:

* [Agent]({{ site.baseurl }}services/#agent): runs on each and every host and provides a stable network location for *Flask* to stream metrics from.

* [Flask]({{ site.baseurl }}services/#flask): collects metrics coming from the Agent stream and stores them in local memory, partitioned by a given "bucket" (more on this later).

* [Chemist]({{ site.baseurl }}services/#chemist): organising what funnels are pouring which metrics into which flasks. In essence this component is a job manager. Chemist can automatically detect failures in flasks and repartition the load amongst the remaining flasks in the operational cluster.

At a high-level, the system can be visualised like this:

![image]({{ site.baseurl }}img/chemist-flask-funnel.png)

Given the different functions of each component, please refer to their specific sections for more information.

## Installation

Regardless of the *Funnel* service component

## Security

The security model of funnel currently **relies on network level security**; the application does not attempt to security its communication over the wire using any kind of TLS. With this in mind, never ever host a service on the public internet where the *Funnel* network port (typically `5775` for HTTP and/or `7390` for ZMTP) is open. Doing this would leave the system open for tampering and expose sensitive internals to attackers.

In this frame, the recommended security model is to restrict access to datacenter internals, VPNs and specific VLANs. We may add application-layer security at a later date, but given that the system is designed to run fast and light, using operational network security is a fair trade off.

