sys.props.get("plugin.version") match {
  case Some(x) => addSbtPlugin("com.indoorvivants" % "sbt-commandmatrix" % x)
  case _ =>
    sys.error(
      """|The system property 'plugin.version' is not defined.
                         |Specify this property using the scriptedLaunchOpts -D.""".stripMargin
    )
}

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.20.2")
addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.10.0")
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.5.10")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.6")
