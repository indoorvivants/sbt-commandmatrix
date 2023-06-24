lazy val core = project
  .in(file("core"))
  .settings(name := "sbt-commandmatrix")
  .enablePlugins(SbtPlugin)
  .settings(
    addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.9.1"),
    scriptedLaunchOpts := {
      scriptedLaunchOpts.value ++
        Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
    },
    scriptedBufferLog := false
  )

publish / skip := true

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
