import sbt.VirtualAxis
import commandmatrix._

val catsEffectDimension =
  Dimension.create("CE2") {
    case CatsEffect2Axis => "CE2"
    case CatsEffect3Axis => "CE3"
  }

inThisBuild(
  Seq(
    commands ++= CrossCommand.aliased(
      "run",
      "check",
      matrices = Seq(core),
      dimensions = Seq(
        catsEffectDimension,
        Dimension.scala("2.12", fullFor3 = true),
        Dimension.platform()
      )
    ),
    commands ++= CrossCommand.composite(
      "codeQuality",
      Seq(
        "scalafmtCheckAll",
        "unusedCompileDependenciesTest",
        "undeclaredCompileDependenciesTest"
      ),
      matrices = Seq(core),
      dimensions = Seq(
        catsEffectDimension,
        // we use `fullFor3` to create a command
        // for full Scala 3 version (as opposed to just the major.minor)
        Dimension.scala("2.12", fullFor3 = true),
        Dimension.platform()
      ),
      // don't run codeQuality checks for Scala 3 projects
      // We do this for two reasons:
      // 1. We're using a version of scalafmt that doesn't work with Scala 3
      // 2. sbt-explicit-dependencies plugin has issues with Scala.js projects
      filter = axes =>
        CrossCommand.filter.notScala3(axes) &&
          CrossCommand.filter.onlyJvm(axes),
      // for projects that don't pass the filter above
      // we can create a noop command
      // this helps on CI
      stubMissing = true
    ),
    allowUnsafeScalaLibUpgrade := true
  )
)

val scala3Versions = Seq(
  "3.3.7",
  "3.8.2"
)

val scala2Versions = Seq(
  "2.12.20",
  "2.13.17"
)

val scalaVersions = scala3Versions ++ scala2Versions

lazy val root = project
  .in(file("."))
  .aggregate(core.projectRefs: _*)

import commandmatrix.extra.*

lazy val core =
  projectMatrix
    .in(file("core"))
    .someVariations(
      scalaVersions = scalaVersions,
      axes = List(CatsEffect2Axis, CatsEffect3Axis),
      List(VirtualAxis.js, VirtualAxis.jvm)
    )(
      MatrixAction
        .ForAxes(axes =>
          axes.contains(VirtualAxis.js) && axes.contains(CatsEffect2Axis)
        )
        .Settings(
          ce2Settings ++ jsSettings
        ),
      MatrixAction
        .ForAxes(axes =>
          axes.contains(VirtualAxis.jvm) && axes.contains(CatsEffect2Axis)
        )
        .Settings(
          ce2Settings
        ),
      MatrixAction
        .ForAxes(axes =>
          axes.contains(VirtualAxis.jvm) && axes.contains(CatsEffect3Axis)
        )
        .Settings(
          ce3Settings
        ),
      MatrixAction
        .ForAxes(axes =>
          axes.contains(VirtualAxis.js) && axes.contains(CatsEffect3Axis)
        )
        .Settings(
          ce3Settings ++ jsSettings
        )
    )

lazy val jsSettings = Seq(
  scalaJSUseMainModuleInitializer := true
)

lazy val ce3Settings = Seq(
  libraryDependencies += "org.typelevel" %%% "cats-effect" % "3.7.0"
)

lazy val ce2Settings = Seq(
  libraryDependencies += "org.typelevel" %%% "cats-effect" % "2.5.5"
)
