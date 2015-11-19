//: ----------------------------------------------------------------------------
//: Copyright (C) 2015 Verizon.  All Rights Reserved.
//:
//:   Licensed under the Apache License, Version 2.0 (the "License");
//:   you may not use this file except in compliance with the License.
//:   You may obtain a copy of the License at
//:
//:       http://www.apache.org/licenses/LICENSE-2.0
//:
//:   Unless required by applicable law or agreed to in writing, software
//:   distributed under the License is distributed on an "AS IS" BASIS,
//:   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//:   See the License for the specific language governing permissions and
//:   limitations under the License.
//:
//: ----------------------------------------------------------------------------
package funnel

/**
 * An `Edge` instrument provides inter-service telemetry. Its purpose is to
 * monitor the status and timings of service-to-service connections.
 *
 * The `origin` is the actual origin of the connection. The `destination` is
 * where the connection is being made to. The `timer` is a measure of some
 * latency or roundtrip timing between the origin and destination, and the
 * `status` is some notion of the overall status of the connection
 * (Red, Amber, or Green).
 *
 * In a typical use case, we're making a connection from one service to
 * another, relying on some discovery service or load balancer to provide us
 * with an actual host to connect to. The `origin` is the local hostname,
 * and the `destination` should be the actual remote hostname. Each time we are
 * redirected to a new host for the service, we should set the `destination`
 * to the name of that host. When we make a request across the connection, we
 * should time it with the edge `timer`. If we detect that the remote host is
 * down, we should set the `status` traffic light to `Red`. In a connection
 * configured with a circuit-breaker, we can use an `Amber` status to indicate
 * the "half-open" state.
 *
 * An `Edge` should be constructed using the [[Instruments.edge]] method.
 */
case class Edge private [funnel] (
  origin: ContinuousGauge[Edge.Origin],
  destination: ContinuousGauge[Edge.Destination],
  timer: Timer[Periodic[Stats]],
  status: TrafficLight
)
object Edge {
  type Origin = String
  type Destination = String
}
