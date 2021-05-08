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
        Dimension.scala("2.12", fullFor3 = true),
        Dimension.platform()
      ),
      // don't run codeQuality checks for Scala 3 projects
      filter = axes =>
        CrossCommand.filter.notScala3(axes) &&
          CrossCommand.filter.onlyJvm(axes)
    )
  )
)

val scalaVersions = Seq(
  "2.12.13",
  "2.13.5",
  "3.0.0-RC2",
  "3.0.0-RC3"
)

lazy val root = project
  .in(file("."))
  .aggregate(core.projectRefs: _*)

lazy val core =
  projectMatrix
    .in(file("core"))
    .customRow(
      scalaVersions = scalaVersions,
      axisValues = Seq(CatsEffect2Axis, VirtualAxis.js),
      process = (_: Project)
        .settings(ce2Settings ++ jsSettings)
        .enablePlugins(ScalaJSPlugin)
    )
    .customRow(
      scalaVersions = scalaVersions,
      axisValues = Seq(CatsEffect2Axis, VirtualAxis.jvm),
      settings = ce2Settings
    )
    .customRow(
      scalaVersions = scalaVersions,
      axisValues = Seq(CatsEffect3Axis, VirtualAxis.js),
      process = (_: Project)
        .settings(ce2Settings ++ jsSettings)
        .enablePlugins(ScalaJSPlugin)
    )
    .customRow(
      scalaVersions = scalaVersions,
      axisValues = Seq(CatsEffect3Axis, VirtualAxis.jvm),
      settings = ce3Settings
    )

lazy val jsSettings = Seq(
  scalaJSUseMainModuleInitializer := true
)

lazy val ce3Settings = Seq(
  libraryDependencies += "org.typelevel" %%% "cats-effect" % "3.1.0"
)

lazy val ce2Settings = Seq(
  libraryDependencies += "org.typelevel" %%% "cats-effect" % "2.5.0"
)
