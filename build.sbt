// *****************************************************************************
// Projects
// *****************************************************************************

lazy val scalaChain =
  project
    .in(file("."))
    .enablePlugins(AutomateHeaderPlugin, GitVersioning, GitBranchPrompt)
    .settings(settings)
    .settings(
      libraryDependencies ++= Seq(
        library.cats,
        library.log4s,
        library.logback,
        library.scrypto,
        library.scalaTest % "test"
      )
    )

// *****************************************************************************
// Library dependencies
// *****************************************************************************
resolvers += "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/"

lazy val library =
  new {
    val cats: ModuleID    = "org.typelevel"        %% "cats"           % "0.9.0"
    val log4s             = "org.log4s"            %% "log4s"          % "1.3.5"
    val logback           = "ch.qos.logback"       % "logback-classic" % "1.2.3"
    val scrypto: ModuleID = "org.scorexfoundation" %% "scrypto"        % "1.2.1"

    val scalaTest = "org.scalatest" %% "scalatest" % "3.0.1"
  }

// *****************************************************************************
// Settings
// *****************************************************************************

lazy val settings =
commonSettings ++
gitSettings ++
scalafmtSettings

val wartsIgnored = List(Wart.Any, Wart.NonUnitStatements, Wart.DefaultArguments)

lazy val commonSettings =
  Seq(
    scalaVersion := "2.12.3",
    organization := "com.aracon",
    organizationName := "Pere Villega",
    startYear := Some(2017),
    licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
    scalacOptions ++= Seq(
      "-unchecked",
      "-deprecation",
      "-language:_",
      "-target:jvm-1.8",
      "-encoding",
      "UTF-8"
    ),
    unmanagedSourceDirectories.in(Compile) := Seq(scalaSource.in(Compile).value),
    unmanagedSourceDirectories.in(Test) := Seq(scalaSource.in(Test).value),
    wartremoverWarnings in (Compile, compile) ++= Warts.unsafe.filterNot(wartsIgnored.contains)
  )

lazy val gitSettings =
  Seq(
    git.useGitDescribe := true
  )

lazy val scalafmtSettings =
  Seq(
    scalafmtOnCompile := true,
    scalafmtVersion := "1.0.0-RC4"
  )
