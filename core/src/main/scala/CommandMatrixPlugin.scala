package commandmatrix
import sbt._
import Keys._
import sbtprojectmatrix.ProjectMatrixPlugin

object CommandMatrixPlugin extends sbt.AutoPlugin {
  override def trigger = allRequirements
  override def requires = ProjectMatrixPlugin

  object autoImport extends CrossCommands
}

trait CrossCommands {
  import sbt.VirtualAxis
  import sbt.{Command, Project}

  case class Dimension(
      matchName: PartialFunction[VirtualAxis, String],
      default: String
  )

  object Dimension {
    def create(default: String)(pf: PartialFunction[VirtualAxis, String]) =
      Dimension(pf, default)

    def scala(
        ifMissing: String,
        fullFor3: Boolean = true,
        fullFor2: Boolean = false
    ): Dimension = {
      Dimension.create(ifMissing) { case v: VirtualAxis.ScalaVersionAxis =>
        sbt.librarymanagement.CrossVersion
          .partialVersion(v.scalaVersion)
          .filter { case (major, minor) =>
            if (major == 3 && fullFor3) false
            else if (major == 2 && fullFor2) false
            else true
          }
          .map(v => s"${v._1}_${v._2}")
          .getOrElse(v.scalaVersion.replace('.', '_'))
      }
    }

    def platform(ifMissing: String = VirtualAxis.jvm.value): Dimension =
      Dimension.create(ifMissing) { case v: VirtualAxis.PlatformAxis =>
        v.value
      }
  }

  def crossCommand(
      cmd: String,
      alias: Option[String],
      matrix: Seq[sbt.internal.ProjectMatrix],
      dimensions: Seq[Dimension]
  ): Seq[Command] = {
    val allProjects = matrix.flatMap(_.allProjects())

    val buckets = List.newBuilder[(Seq[String], Project)]

    allProjects.foreach { case (proj, axes) =>
      val found = dimensions.map(dim =>
        axes.collectFirst(dim.matchName).getOrElse(dim.default)
      )
      buckets += (found -> proj)
    }

    buckets.result
      .groupBy(_._1)
      .map { case (segments, results) =>
        val projects = results.map(_._2.id)
        val groupCmd = alias.getOrElse(cmd) + "-" + segments.mkString("-")

        groupCmd -> projects.map(id => id + "/" + cmd)
      }
      .toSeq
      .map { case (alias, subcommands) =>
        Command.command(alias) { state =>
          subcommands.foldRight(state)(_ :: _)
        }

      }
  }

}
