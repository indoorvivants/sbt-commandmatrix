lazy val scala212 = "2.12.14"
lazy val scala213 = "2.13.6"
lazy val scala3 = "3.1.0-RC1"

import commandmatrix.extra._
import sbt.VirtualAxis

lazy val core = projectMatrix
  .in(file("core"))
  .someVariations(
    List(scala212, scala213, scala3),
    List(VirtualAxis.jvm, VirtualAxis.js, VirtualAxis.native),
    List(LibraryAxis.V1, LibraryAxis.V2)
  ) {
    // 1: Completely remove the `Scala 3 + Scala Native` combination
    case (scalaV, axes)
        if scalaV.isScala3 && axes.contains(VirtualAxis.native) =>
      MatrixAction.Skip

    // 2: Disable Scalafix plugin for all Scala 3 projects
    case (scalaV, _) if scalaV.isScala3 =>
      MatrixAction.Configure(_.disablePlugins(ScalafixPlugin))

    // 3: Not publish any of the Scala 2 projects on Scala.js
    case (scalaV, axes) if !scalaV.isScala3 && axes.contains(VirtualAxis.js) =>
      MatrixAction.Settings(
        Seq(
          publish / skip := true,
          publishLocal / skip := true
        )
      )
  }
  .settings(
    notPublished := {
      if ((publish / skip).value) () else throw new Exception("what")
    }
  )

val notPublished = taskKey[Unit]("what")
