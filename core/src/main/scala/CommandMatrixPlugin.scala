/*
 * Copyright 2021 Anton Sviridov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package commandmatrix
import sbt._
import Keys._
import sbtprojectmatrix.ProjectMatrixPlugin
import sbt.internal.ProjectMatrix
import scala.collection.immutable
import scala.util.Try

object CommandMatrixPlugin extends sbt.AutoPlugin {
  override def trigger = allRequirements
  override def requires = ProjectMatrixPlugin

  object autoImport extends Experimental

}

trait Experimental {
  import CommandType._

  object CrossCommand {
    def single(
        cmd: String,
        matrices: Seq[sbt.internal.ProjectMatrix],
        dimensions: Seq[Dimension],
        filter: Seq[VirtualAxis] => Boolean = _ => true,
        stubMissing: Boolean = false
    ): Seq[Command] =
      crossCommand(
        Single(cmd, stubMissing),
        matrices = matrices,
        dimensions = dimensions,
        filter = filter
      )

    def all(
        cmds: Seq[String],
        matrices: Seq[sbt.internal.ProjectMatrix],
        dimensions: Seq[Dimension],
        filter: Seq[VirtualAxis] => Boolean = _ => true,
        stubMissing: Boolean = false
    ): Seq[Command] =
      cmds.flatMap(cmd =>
        crossCommand(
          command = Single(cmd, stubMissing),
          matrices = matrices,
          dimensions = dimensions,
          filter = filter
        )
      )

    def aliased(
        cmd: String,
        alias: String,
        matrices: Seq[sbt.internal.ProjectMatrix],
        dimensions: Seq[Dimension],
        filter: Seq[VirtualAxis] => Boolean = _ => true,
        stubMissing: Boolean = false
    ) = crossCommand(
      SingleAliased(cmd, alias, stubMissing),
      matrices = matrices,
      dimensions = dimensions,
      filter = filter
    )

    def composite(
        name: String,
        commands: Seq[String],
        matrices: Seq[sbt.internal.ProjectMatrix],
        dimensions: Seq[Dimension],
        filter: Seq[VirtualAxis] => Boolean = _ => true,
        stubMissing: Boolean = false
    ) = crossCommand(
      command = Composite(name, commands, stubMissing),
      matrices = matrices,
      dimensions = dimensions,
      filter = filter
    )

    object filter {
      def isScalaBinary(major: Long, minor: Option[Long] = None)(
          axes: Seq[VirtualAxis]
      ): Boolean =
        axes
          .collectFirst { case sv: VirtualAxis.ScalaVersionAxis =>
            sv.scalaVersion
          }
          .flatMap { v =>
            sbt.librarymanagement.CrossVersion.partialVersion(v)
          } match {
          case Some((scalaMajor, scalaMinor)) =>
            minor match {
              case Some(min) => (scalaMajor, scalaMinor) == (major, min)
              case None      => scalaMajor == major
            }
          case None => false // no valid Scala axis at all
        }

      def isScalaMajor(major: Long)(axes: Seq[VirtualAxis]) =
        isScalaBinary(major, None)(axes)

      def notScala3(axes: Seq[VirtualAxis]): Boolean =
        !isScalaMajor(3L)(axes)

      def onlyScala3(axes: Seq[VirtualAxis]): Boolean =
        isScalaMajor(3L)(axes)

      def notScala2(axes: Seq[VirtualAxis]): Boolean =
        !isScalaMajor(2L)(axes)

      def onlyScala2(axes: Seq[VirtualAxis]): Boolean =
        isScalaMajor(2L)(axes)

      def onlyJvm(axes: Seq[VirtualAxis]): Boolean =
        axes.contains(VirtualAxis.jvm)

      def onlyJs(axes: Seq[VirtualAxis]): Boolean =
        axes.contains(VirtualAxis.js)

      def onlyNative(axes: Seq[VirtualAxis]): Boolean =
        axes.contains(VirtualAxis.native)

      def notJvm(axes: Seq[VirtualAxis]): Boolean =
        !onlyJvm(axes)

      def notJs(axes: Seq[VirtualAxis]): Boolean =
        !onlyJs(axes)

      def notNative(axes: Seq[VirtualAxis]): Boolean =
        !onlyNative(axes)
    }

  }

  private[commandmatrix] sealed abstract class CommandType(
      val stubMissing: Boolean
  ) extends Product
      with Serializable

  private[commandmatrix] object CommandType {
    case class Single(command: String, override val stubMissing: Boolean)
        extends CommandType(stubMissing)
    case class SingleAliased(
        command: String,
        alias: String,
        override val stubMissing: Boolean
    ) extends CommandType(stubMissing)
    case class Composite(
        command: String,
        delegates: Seq[String],
        override val stubMissing: Boolean
    ) extends CommandType(stubMissing)
  }

  private[commandmatrix] def crossCommand(
      command: CommandType,
      matrices: Seq[sbt.internal.ProjectMatrix],
      dimensions: Seq[Dimension],
      filter: Seq[VirtualAxis] => Boolean
  ): Seq[Command] = {
    val allProjects = matrices.flatMap(_.allProjects())

    val buckets = List.newBuilder[(Seq[String], (Project, Boolean))]

    val resultingCommandName = command match {
      case Single(name, _)            => name
      case SingleAliased(_, alias, _) => alias
      case Composite(name, _, _)      => name
    }

    val rawCommands = command match {
      case Composite(_, delegates, _) => delegates
      case Single(name, _)            => Seq(name)
      case SingleAliased(name, _, _)  => Seq(name)
    }

    val stubMissing = command.stubMissing

    allProjects
      .map(p => (p._1, p._2, filter(p._2)))
      .filter { case (_, _, include) =>
        include || stubMissing
      }
      .foreach { case (proj, axes, include) =>
        val found = dimensions
          .map(dim => axes.collectFirst(dim.matchName).getOrElse(dim.default))
        buckets += found -> (proj, include)
      }

    buckets.result
      .groupBy(_._1)
      .map { case (segments, results) =>
        val projects = results
        val groupCmd = resultingCommandName + "-" + segments.mkString("-")

        val components = for {
          proj <- projects
          id = proj._2._1.id
          include = proj._2._2
          cmd <- rawCommands

          result = if (!include && stubMissing) "" else id + "/" + cmd
        } yield result

        groupCmd -> components.filter(_ != "")
      }
      .toSeq
      .map { case (alias, subcommands) =>
        Command.command(alias) { state =>
          subcommands.foldRight(state)(_ :: _)
        }

      }
  }

}

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

object extra {
  sealed trait MatrixAction extends Product with Serializable

  object MatrixAction {

    case class Act(
        selector: (MatrixScalaVersion, Seq[VirtualAxis]) => Boolean,
        what: MatrixAction
    )
    object Act {
      case object Skip extends MatrixAction
      case object Keep extends MatrixAction
      case class Configure(f: Project => Project) extends MatrixAction
      case class Settings(set: Seq[Def.Setting[_]]) extends MatrixAction
    }

    class ActBuilder(
        selector: (MatrixScalaVersion, Seq[VirtualAxis]) => Boolean
    ) {
      def Skip = Act(selector, Act.Skip)
      def Keep = Act(selector, Act.Keep)
      def Configure(f: Project => Project) =
        Act(selector, Act.Configure(f))
      def Settings(set: Seq[Def.Setting[_]]) =
        Act(selector, Act.Settings(set))
    }

    def ForScala(selector: MatrixScalaVersion => Boolean) =
      new ActBuilder((scalaV, _) => selector(scalaV))

    def ForAxes(selector: Seq[VirtualAxis] => Boolean) =
      new ActBuilder((_, axes) => selector(axes))

    def ForAll = new ActBuilder((_, _) => true)

    def ForPlatforms(selector: VirtualAxis.PlatformAxis => Boolean) =
      new ActBuilder((_, axes) =>
        axes.collectFirst {
          case p: VirtualAxis.PlatformAxis if selector(p) => p
        }.nonEmpty
      )

    def apply(selector: (MatrixScalaVersion, Seq[VirtualAxis]) => Boolean) =
      new ActBuilder(selector)

    def ForPlatform(target: VirtualAxis.PlatformAxis => Boolean) =
      new ActBuilder((_, axes) =>
        axes.collectFirst {
          case p: VirtualAxis.PlatformAxis if p == target => p
        }.nonEmpty
      )

  }

  case class MatrixScalaVersion(value: String) {
    def isScala3 = value.startsWith("3.")
    def isScala2 = value.startsWith("2.")
  }

  implicit final class ProjectMatrixExtraOps(val pm: ProjectMatrix)
      extends AnyVal {

    // Copied from https://github.com/sbt/sbt-projectmatrix/blob/develop/src/main/scala/sbt/internal/ProjectMatrix.scala#L405
    // To be removed when projectmatrix allows callnig builders
    // with custom axes
    // Note that this is taking advantage of unintentionally public ReflectionUtil
    // If projectmatrix removes it, we can inline it
    private def scalajsPlugin(classLoader: ClassLoader): Try[AutoPlugin] = {
      import sbtprojectmatrix.ReflectionUtil._
      withContextClassloader(classLoader) { loader =>
        getSingletonObject[AutoPlugin](
          loader,
          "org.scalajs.sbtplugin.ScalaJSPlugin$"
        )
      }
    }

    private def nativePlugin(classLoader: ClassLoader): Try[AutoPlugin] = {
      import sbtprojectmatrix.ReflectionUtil._
      withContextClassloader(classLoader) { loader =>
        getSingletonObject[AutoPlugin](
          loader,
          "scala.scalanative.sbtplugin.ScalaNativePlugin$"
        )
      }
    }

    private def enableScalaJSPlugin(project: Project): Project =
      project.enablePlugins(
        scalajsPlugin(this.getClass.getClassLoader).getOrElse(
          sys.error(
            """Scala.js plugin was not found. Add the sbt-scalajs plugin into project/plugins.sbt:
                    |  addSbtPlugin("org.scala-js" % "sbt-scalajs" % "x.y.z")
                    |""".stripMargin
          )
        )
      )

    private def enableScalaNativePlugin(project: Project): Project =
      project.enablePlugins(
        nativePlugin(this.getClass.getClassLoader).getOrElse(
          sys.error(
            """Scala Native plugin was not found. Add the sbt-scala-native plugin into project/plugins.sbt:
                    |  addSbtPlugin("org.scala-native" % "sbt-scala-native" % "x.y.z")
                    |""".stripMargin
          )
        )
      )

    def someVariations(scalaVersions: List[String], axes: List[VirtualAxis]*)(
        conf: MatrixAction.Act*
    ): ProjectMatrix = {
      def go(sq: List[List[VirtualAxis]]): List[List[VirtualAxis]] = {
        sq match {
          case Nil         => List.empty
          case head :: Nil => head.map(List(_))
          case head :: tl =>
            val rest = go(tl)
            head.flatMap { va =>
              rest.map(va :: _)
            }
        }
      }

      def results(
          scalaV: MatrixScalaVersion,
          axes: List[VirtualAxis]
      ): List[MatrixAction] =
        conf.toList.collect {
          case ma if ma.selector(scalaV, axes) => ma.what
        }

      val actions = for {
        scalaV <- scalaVersions
        axisValues <- go(axes.map(_.toList).toList).map(_.reverse)
      } yield (
        scalaV,
        axisValues,
        results(MatrixScalaVersion(scalaV), axisValues)
      )

      def collapse(
          actions: Seq[MatrixAction]
      ): Either[Project => Project, Seq[Def.Setting[_]]] = {
        import MatrixAction.Act
        val settings = actions.collect { case Act.Settings(setts) =>
          setts
        }.flatten

        val configures = actions.collect { case Act.Configure(f) =>
          f
        }

        if (configures.nonEmpty) {
          val fin = configures.reduce(_ andThen _).andThen(_.settings(settings))

          Left(fin)
        } else {
          Right(settings)
        }

      }

      val enableSJS = MatrixAction.Act.Configure(enableScalaJSPlugin)
      val enableSN = MatrixAction.Act.Configure(enableScalaNativePlugin)

      def extra(axes: Seq[VirtualAxis]): List[MatrixAction] =
        if (axes.contains(VirtualAxis.js)) List(enableSJS)
        else if (axes.contains(VirtualAxis.native)) List(enableSN)
        else Nil

      actions.foldLeft(pm) { case (current, (scalaV, axes, actions)) =>
        val axesStr = axes.mkString(", ")
        actions match {
          case l if l.contains(MatrixAction.Act.Skip) => current
          case other =>
            val collapsed = collapse(other ++ extra(axes))
            collapsed match {
              case Left(configure) =>
                current.customRow(Seq(scalaV), axes, configure)
              case Right(settings) =>
                current.customRow(Seq(scalaV), axes, settings)
            }
        }
      }
    }

    def allVariations(
        scalaVersions: List[String],
        axes: List[VirtualAxis]*
    ): ProjectMatrix =
      someVariations(scalaVersions, axes: _*)(MatrixAction.ForAll.Keep)
  }
}
