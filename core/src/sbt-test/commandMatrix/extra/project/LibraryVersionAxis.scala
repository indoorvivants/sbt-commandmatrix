import sbt.VirtualAxis

sealed abstract class LibraryAxis(
    val idSuffix: String,
    val directorySuffix: String
) extends VirtualAxis.WeakAxis

object LibraryAxis {
  case object V1 extends LibraryAxis("-V1", "-v1")
  case object V2 extends LibraryAxis("-V2", "-v2")
}
