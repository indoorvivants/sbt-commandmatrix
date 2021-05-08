import sbt.VirtualAxis

class CatsEffectAxis(val idSuffix: String, val directorySuffix: String)
    extends VirtualAxis.WeakAxis

object CatsEffect2Axis extends CatsEffectAxis("_CE2", "ce2")
object CatsEffect3Axis extends CatsEffectAxis("_CE3", "ce3")
