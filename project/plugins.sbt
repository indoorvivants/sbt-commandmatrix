addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.11.0")
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.11.2")
addSbtPlugin("com.github.sbt" % "sbt-header" % "5.11.0")

Compile / unmanagedSourceDirectories ++=
  Seq(
    (ThisBuild / baseDirectory).value.getParentFile /
      "core" / "src" / "main" / "scala",
    (ThisBuild / baseDirectory).value.getParentFile /
      "core" / "src" / "main" / "scala-2.12"
  )
