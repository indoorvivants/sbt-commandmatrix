resolvers += Resolver.sonatypeRepo("snapshots")

val versionOverride =
  sys.env.getOrElse("COMMANDMATRIX_VERSION", "0.0.3")

addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.10.0")
addSbtPlugin("com.indoorvivants" % "sbt-commandmatrix" % versionOverride)
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.5.1")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.2")
addSbtPlugin("com.github.cb372" % "sbt-explicit-dependencies" % "0.2.16")
