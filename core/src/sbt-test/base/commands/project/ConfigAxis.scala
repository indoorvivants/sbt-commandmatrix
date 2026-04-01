import sbt.VirtualAxis

case class ConfigAxis(idSuffix: String, directorySuffix: String)
    extends VirtualAxis.WeakAxis

object ConfigAxis {

  lazy val config12 = ConfigAxis("Config1_2", "config1.2")
  lazy val config13 = ConfigAxis("Config1_3", "config1.3")
}
