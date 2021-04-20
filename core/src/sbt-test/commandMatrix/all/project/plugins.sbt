sys.props.get("plugin.version") match {
  case Some(x) => addSbtPlugin("com.indoorvivants" % "sbt-commandmatrix" % x)
  case _ =>
    sys.error(
      """|The system property 'plugin.version' is not defined.
                         |Specify this property using the scriptedLaunchOpts -D.""".stripMargin
    )
}

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.3.0")
addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.7.0-SNAPSHOT")
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.4.0")
