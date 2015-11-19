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

import com.twitter.algebird.{Group, Moments, MomentsGroup}

/** Monoidally track count/mean/variance/skewness/kurtosis. */
class Stats(moments: Moments, val last: Option[Double]) {
  private[funnel] def get = moments
  def count = moments.count
  def mean = moments.mean
  def variance = moments.variance
  def standardDeviation = math.sqrt(variance)
  def skewness = moments.skewness
  def kurtosis = moments.kurtosis

  def ++(d: Double): Stats =
    new Stats(Moments.group.plus(moments, Moments(d)), Some(d))

  def ++(s: Stats): Stats =
    new Stats(Moments.group.plus(moments, s.get), s.last orElse last)

  def ++(ds: Seq[Double]): Stats =
    this ++ Stats.reduce(ds)

  override def toString: String =
    s"Stats(count = $count, mean = $mean, variance = $variance, standardDeviation = $standardDeviation, skewness = $skewness, kurtosis = $kurtosis)"
}

object Stats {
  def apply(d: Double): Stats =
    new Stats(Moments(d), Some(d))

  def reduce(s: Seq[Double]): Stats =
    new Stats(s.view.map(d => Moments(d)).reduce(Moments.group.plus),
              s.lastOption)

  implicit def statsGroup: Group[Stats] = new Group[Stats] {
    def zero = new Stats(MomentsGroup.zero, None)
    override def negate(s: Stats) = new Stats(MomentsGroup.negate(s.get), None)
    def plus(s1: Stats, s2: Stats): Stats = s1 ++ s2
  }
}
