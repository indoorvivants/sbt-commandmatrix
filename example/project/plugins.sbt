resolvers += Resolver.sonatypeRepo("snapshots")

addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.8.0")
addSbtPlugin(
  "com.indoorvivants" % "sbt-commandmatrix" % "0.0.2+5-7e164365-SNAPSHOT"
)
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.5.1")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.2")
addSbtPlugin("com.github.cb372" % "sbt-explicit-dependencies" % "0.2.16")
