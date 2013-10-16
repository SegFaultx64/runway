name := "Runway"

organization := "com.traversalsoftware"

seq(com.github.retronym.SbtOneJar.oneJarSettings: _*)

version := "0.1.0"

scalaVersion := "2.10.0"

libraryDependencies ++= Seq(
	"play" % "play" % "2.1.3",
	"play" % "play-iteratees_2.10" % "2.1.3",
	"org.reactivemongo" %% "play2-reactivemongo" % "0.9",
	"org.mockito" % "mockito-core" % "1.9.5"
)
     
scalacOptions ++= Seq("-feature", "-language:postfixOps")

initialCommands := "import com.traversalsoftware.runway._"

