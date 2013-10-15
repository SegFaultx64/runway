name := "Runway"

organization := "com.traversalsoftware"

version := "0.1.0"

scalaVersion := "2.10.0"

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "1.9.2" % "test"
)
     
scalacOptions ++= Seq("-feature", "-language:postfixOps")

initialCommands := "import com.traversalsoftware.runway._"

libraryDependencies += "org.reactivemongo" %% "play2-reactivemongo" % "0.9"

libraryDependencies += "org.mockito" % "mockito-core" % "1.9.5"
