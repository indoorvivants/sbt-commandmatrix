lazy val core = project
  .in(file("core"))
  .settings(name := "sbt-commandmatrix")
  .enablePlugins(SbtPlugin)
  .settings(
    addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.7.0-SNAPSHOT"),
    scriptedLaunchOpts := {
      scriptedLaunchOpts.value ++
        Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
    },
    scriptedBufferLog := false
  )

publish / skip := true

inThisBuild(
  Seq(
    organization := "com.indoorvivants"
  )
)
