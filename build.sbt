organization := "org.allenai"

name := "qapra"

scalaVersion := "2.11.2"

scalacOptions ++= Seq("-unchecked", "-deprecation")

javacOptions += "-Xlint:unchecked"

fork in run := true

javaOptions ++= Seq("-Xmx20g")

libraryDependencies ++= Seq(
  "org.scalatest" % "scalatest_2.11" % "2.2.1" % "test",
  "com.github.scala-incubator.io" %% "scala-io-core" % "0.4.3",
  "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.3",
  "edu.cmu.ml.rtw" %%  "pra" % "3.1-SNAPSHOT",
  "org.graphchi" %%  "graphchi-java" % "0.2.1",
  "edu.stanford.nlp" %  "stanford-corenlp" % "3.4.1",
  "edu.stanford.nlp" %  "stanford-corenlp" % "3.4.1" classifier "models"
)

instrumentSettings
