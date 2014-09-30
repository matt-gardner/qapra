organization := "org.allenai"

name := "qapra"

scalaVersion := "2.11.2"

scalacOptions ++= Seq("-unchecked", "-deprecation")

javacOptions += "-Xlint:unchecked"

resolvers += Resolver.url("https://raw.github.com/matt-gardner/graphchi-java/mvn-repo/")

libraryDependencies ++= Seq(
  "org.scalatest" % "scalatest_2.11" % "2.2.1" % "test",
  "com.github.scala-incubator.io" %% "scala-io-core" % "0.4.3",
  "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.3",
  "edu.cmu.ml.rtw" %%  "pra" % "1.0-SNAPSHOT" changing(),
  "groupId" %  "graphchi-java" % "0.2",
  "edu.stanford.nlp" %  "stanford-corenlp" % "3.4.1",
  "edu.stanford.nlp" %  "stanford-corenlp" % "3.4.1" classifier "models"
)

instrumentSettings
