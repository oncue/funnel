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

object Banner {
  // http://patorjk.com/software/taag/#p=display&h=3&f=Big%20Money-se&t=Chemist
  private val wording = """
  |  ______  __                             __            __
  | /      \|  \                           |  \          |  \
  ||  $$$$$$| $$____   ______  ______ ____  \$$ _______ _| $$_
  || $$   \$| $$    \ /      \|      \    \|  \/       |   $$ \
  || $$     | $$$$$$$|  $$$$$$| $$$$$$\$$$$| $|  $$$$$$$\$$$$$$
  || $$   __| $$  | $| $$    $| $$ | $$ | $| $$\$$    \  | $$ __
  || $$__/  | $$  | $| $$$$$$$| $$ | $$ | $| $$_\$$$$$$\ | $$|  \
  | \$$    $| $$  | $$\$$     | $$ | $$ | $| $|       $$  \$$  $$
  |  \$$$$$$ \$$   \$$ \$$$$$$$\$$  \$$  \$$\$$\$$$$$$$    \$$$$
  |
  |""".stripMargin

  private val suffix = s"""
  | ${Chemist.version}
  | Built at ${BuildInfo.buildDate} with SBT ${BuildInfo.sbtVersion}
  | """.stripMargin

  val text = wording+suffix
}