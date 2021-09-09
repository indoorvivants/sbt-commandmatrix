import commandmatrix._

lazy val configKey = settingKey[String]("")

lazy val scala212 = "2.12.13"
lazy val scala213 = "2.13.5"
lazy val scala3 = "3.1.0-RC1"

import ConfigAxis._
import commandmatrix.extra._
import sbt.VirtualAxis

lazy val core = projectMatrix
  .in(file("core"))
  .someVariations(
    List(scala212, scala213, scala3),
    List(VirtualAxis.jvm, VirtualAxis.js),
    List(ConfigAxis.config12, ConfigAxis.config13)
  ) {
    case (scalaV, axes) if(scalaV.isScala3 && axes.contains(ConfigAxis.config12)) => 
      MatrixAction.Skip

    case (scalaV, _) if scalaV.value.startsWith("2.12") => 
      MatrixAction.Configure(_.settings(ok := true))

    case (_, axes) if axes.contains(VirtualAxis.js) => 
      MatrixAction.Settings(Seq(ok := true))
  }
  .settings(
    verify := {
      if(ok.value) () else throw new Exception("what") 
    }
  )

val ok = settingKey[Boolean]("bla")
ThisBuild / ok := false
val verify = taskKey[Unit]("what")

