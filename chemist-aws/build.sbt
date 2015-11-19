
common.settings

common.revolver

common.fatjar

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk-ec2"            % V.aws,
  "com.amazonaws" % "aws-java-sdk-autoscaling"    % V.aws,
  "com.amazonaws" % "aws-java-sdk-sns"            % V.aws,
  "com.amazonaws" % "aws-java-sdk-sqs"            % V.aws,
  "com.amazonaws" % "aws-java-sdk-cloudformation" % V.aws
)

fork in Test := false

mainClass in run := Some("funnel.chemist.aws.Main")
