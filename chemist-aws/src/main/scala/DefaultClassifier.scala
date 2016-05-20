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
package chemist
package aws

import scalaz.concurrent.Task
import scalaz.Kleisli

/**
 * This default implementation does not properly handle the various upgrade
 * cases that you might encounter when migrating from one set of clusters to
 * another, but it instead provided as a default where all available flasks
 * and chemists are are "active". There are a set of upgrade scenarios where
 * you do not want to mirror from an existing flask cluster, so they are not
 * targets, nor are they active flasks.
 *
 * Providing this function with a task return type so that extensions can do I/O
 * if they need too (clearly a cache locally would be needed in that case)
 *
 * It is highly recommended you override this with your own classification logic.
 */
object DefaultClassifier extends Classifier[Aws,AwsInstance] {
  import Classification._

  private[funnel] val Flask = "flask"
  private[funnel] val Chemist = "chemist"

  def isApplication(prefix: String)(i: AwsInstance): Boolean =
    i.application.exists(_.name.trim.toLowerCase.startsWith(prefix))

  def isFlask(i: AwsInstance): Boolean =
    isApplication(Flask)(i)

  def isChemist(i: AwsInstance): Boolean =
    isApplication(Chemist)(i)

  def classify(instance: AwsInstance): Task[Classification] =
    Task.delay {
      if (isFlask(instance)) ActiveFlask
      else if (isChemist(instance)) ActiveChemist
      else ActiveTarget
    }
}
