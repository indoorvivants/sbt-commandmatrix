import commandmatrix._

lazy val configKey = settingKey[String]("")

lazy val scala212 = "2.12.13"
lazy val scala213 = "2.13.5"

import ConfigAxis._

lazy val core = projectMatrix
  .in(file("core"))
  .jvmPlatform(Seq(scala213, scala212))
  .jsPlatform(Seq(scala212))
  .settings(
    verify := {
      val base = (ThisBuild / baseDirectory).value
      val platform = virtualAxes.value.collectFirst {
        case v: VirtualAxis.PlatformAxis => v.value
      }.head

      val name = s"result-verify-$platform-${scalaVersion.value}"

      IO.touch(base / name)
    }
  )

lazy val custom = projectMatrix
  .in(file("custom"))
  .customRow(
    scalaVersions = Seq(scala213, scala212),
    axisValues = Seq(config12, VirtualAxis.jvm),
    Seq(configKey := "1.2")
  )
  .customRow(
    scalaVersions = Seq(scala213, scala212),
    axisValues = Seq(config13, VirtualAxis.jvm),
    Seq(configKey := "1.3")
  )
  .settings(verifyCustom := {
    val base = (ThisBuild / baseDirectory).value
    val platform = virtualAxes.value.collectFirst {
      case v: VirtualAxis.PlatformAxis => v.value
    }.head

    val name =
      s"result-custom-$platform-${scalaVersion.value}-${configKey.value}"

    IO.touch(base / name)
  })

lazy val verify = taskKey[Unit]("")
lazy val verifyCustom = taskKey[Unit]("")

inThisBuild(
  Seq(
    commands ++= CrossCommand.single(
      "verify",
      matrices = Seq(core),
      dimensions = Seq(
        Dimension.scala("2.12"),
        Dimension.platform()
      )
    ),
    commands ++= CrossCommand.single(
      "verifyCustom",
      matrices = Seq(custom),
      dimensions = Seq(
        Dimension.scala("2.12"),
        Dimension.platform(),
        Dimension.create("config12") { case s: ConfigAxis =>
          if (s == config12) "config12" else "config13"
        }
      )
    )
  )
)
