name := "Runway"

organization := "com.traversalsoftware"

seq(com.github.retronym.SbtOneJar.oneJarSettings: _*)

version := "0.1.0"

scalaVersion := "2.10.0"

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "1.9.2" % "test"
)
     
scalacOptions ++= Seq("-feature", "-language:postfixOps")

initialCommands := "import com.traversalsoftware.runway._"
