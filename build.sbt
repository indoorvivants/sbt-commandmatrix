lazy val core = projectMatrix
  .in(file("core"))
  .settings(name := "sbt-commandmatrix")
  .enablePlugins(SbtPlugin)
  .jvmPlatform(Seq(Ver.sbt1ScalaVersion, Ver.sbt2ScalaVersion))
  .settings(
    (pluginCrossBuild / sbtVersion) := {
      scalaBinaryVersion.value match {
        case "2.12" => "1.10.0"
        case _      => "2.0.0-RC9"
      }
    },
    scalacOptions ++= {
      scalaBinaryVersion.value match {
        case "2.12" => "-Xsource:3" :: Nil
        case _      => Nil
      }
    },
    sbtTestDirectory := {
      scalaBinaryVersion.value match {
        case "2.12" => (sourceDirectory).value / "sbt-test"
        case _      => (sourceDirectory).value / "sbt-test-sbt2"
      }
    },
    scriptedLaunchOpts := {
      scriptedLaunchOpts.value ++
        Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
    },
    scriptedBufferLog := false,
    libraryDependencies ++= {
      if (scalaBinaryVersion.value == "2.12") {
        val sbtBinV = (pluginCrossBuild / sbtBinaryVersion).value
        val scalaBinV = scalaBinaryVersion.value
        Seq(
          Defaults.sbtPluginExtra(
            "com.eed3si9n" % "sbt-projectmatrix" % "0.11.0",
            sbtBinV,
            scalaBinV
          )
        )
      } else {
        Seq.empty
      }
    }
  )

publish / skip := true

import commandmatrix.extra.*

val Ver = new {
  val scala3Next = "3.8.2"
  val scala3LTS = "3.3.7"

  val sbt1ScalaVersion = "2.12.20"
  val sbt2ScalaVersion = "3.8.2"

}

lazy val example =
  projectMatrix
    .in(file("sample"))
    .someVariations(
      List("2.12.20", "2.13.16", Ver.scala3LTS, Ver.scala3Next),
      List(VirtualAxis.jvm)
    )(
      MatrixAction
        .ForScala(_.value == Ver.scala3Next)
        .Settings(Seq(name := "example3Next")),
      MatrixAction
        .ForScala(_.value == Ver.scala3LTS)
        .Settings(Seq(name := "example3LTS"))
    )
    .settings(
      publish / skip := true,
      publishLocal / skip := true
    )

lazy val dumpVersion = taskKey[Unit]("Dump version")
ThisBuild / dumpVersion := {
  IO.write(file("version.txt"), (core.jvm(true) / version).value)
}

inThisBuild(
  Seq(
    version := {
      val orig = (ThisBuild / version).value
      if (orig.endsWith("-SNAPSHOT") && !sys.env.contains("CI"))
        "0.1.0-SNAPSHOT"
      else orig
    },
    organization := "com.indoorvivants",
    organizationName := "Anton Sviridov",
    homepage := Some(url("https://github.com/indoorvivants/sbt-commandmatrix")),
    startYear := Some(2021),
    licenses := List(
      "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")
    ),
    developers := List(
      Developer(
        "velvetbaldmime",
        "Anton Sviridov",
        "keynmol@gmail.com",
        url("https://blog.indoorvivants.com")
      )
    )
  )
)

addCommandAlias("ci", "scripted;publishLocal")
