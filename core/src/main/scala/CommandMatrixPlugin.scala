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
        filter: Seq[VirtualAxis] => Boolean = _ => true
    ): Seq[Command] =
      crossCommand(
        Single(cmd),
        matrices = matrices,
        dimensions = dimensions,
        filter = filter
      )

    def all(
        cmds: Seq[String],
        matrices: Seq[sbt.internal.ProjectMatrix],
        dimensions: Seq[Dimension],
        filter: Seq[VirtualAxis] => Boolean = _ => true
    ): Seq[Command] =
      cmds.flatMap(cmd =>
        crossCommand(
          command = Single(cmd),
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
        filter: Seq[VirtualAxis] => Boolean = _ => true
    ) = crossCommand(
      SingleAliased(cmd, alias),
      matrices = matrices,
      dimensions = dimensions,
      filter = filter
    )

    def composite(
        name: String,
        commands: Seq[String],
        matrices: Seq[sbt.internal.ProjectMatrix],
        dimensions: Seq[Dimension],
        filter: Seq[VirtualAxis] => Boolean = _ => true
    ) = crossCommand(
      command = Composite(name, commands),
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

  private[commandmatrix] sealed trait CommandType
      extends Product
      with Serializable

  private[commandmatrix] object CommandType {
    case class Single(command: String) extends CommandType
    case class SingleAliased(command: String, alias: String) extends CommandType
    case class Composite(command: String, delegates: Seq[String])
        extends CommandType
  }

  private[commandmatrix] def crossCommand(
      command: CommandType,
      matrices: Seq[sbt.internal.ProjectMatrix],
      dimensions: Seq[Dimension],
      filter: Seq[VirtualAxis] => Boolean
  ): Seq[Command] = {
    val allProjects = matrices.flatMap(_.allProjects())

    val buckets = List.newBuilder[(Seq[String], Project)]

    val resultingCommandName = command match {
      case Single(name)            => name
      case SingleAliased(_, alias) => alias
      case Composite(name, _)      => name
    }

    val rawCommands = command match {
      case Composite(_, delegates) => delegates
      case Single(name)            => Seq(name)
      case SingleAliased(name, _)  => Seq(name)
    }

    allProjects
      .filter(p => filter(p._2))
      .foreach { case (proj, axes) =>
        val found = dimensions
          .map(dim => axes.collectFirst(dim.matchName).getOrElse(dim.default))
        buckets += (found -> proj)
      }

    buckets.result
      .groupBy(_._1)
      .map { case (segments, results) =>
        val projects = results.map(_._2.id)
        val groupCmd = resultingCommandName + "-" + segments.mkString("-")

        val components = for {
          projectId <- projects
          cmd <- rawCommands
        } yield projectId + "/" + cmd

        groupCmd -> components
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
