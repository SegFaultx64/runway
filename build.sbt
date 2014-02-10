import scalariform.formatter.preferences._

name := "Runway"

organization := "com.traversalsoftware"

version := "0.1.0"

scalaVersion := "2.10.3"

resolvers += "Typesafe repository releases" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
	"org.reactivemongo" %% "play2-reactivemongo" % "0.10.2",
	"org.mockito" % "mockito-core" % "1.9.5",
	"org.specs2" %% "specs2" % "2.2.3" % "test",
	"com.typesafe.play" %% "play" % "2.2.1"
)

scalacOptions ++= Seq("-feature", "-language:postfixOps")

initialCommands := "import com.traversalsoftware.runway._"

 scalariformSettings

  ScalariformKeys.preferences := ScalariformKeys.preferences.value
    .setPreference(AlignSingleLineCaseStatements, true)
    .setPreference(AlignParameters, true)
    .setPreference(RewriteArrowSymbols, true)
