sys.props.get("plugin.version") match {
  case Some(x) => addSbtPlugin("com.indoorvivants" % "sbt-commandmatrix" % x)
  case _ =>
    sys.error(
      """|The system property 'plugin.version' is not defined.
                         |Specify this property using the scriptedLaunchOpts -D.""".stripMargin
    )
}
resolvers += "Sonatype Snapshots" at "https://central.sonatype.com/repository/maven-snapshots"

addSbtPlugin(
  "org.scala-native" % "sbt-scala-native" % "0.5.11-20260224-d696e69-SNAPSHOT"
)
