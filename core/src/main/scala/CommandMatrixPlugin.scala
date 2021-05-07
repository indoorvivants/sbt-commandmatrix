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
  object CrossCommand {
    def single(
        cmd: String,
        matrices: Seq[sbt.internal.ProjectMatrix],
        dimensions: Seq[Dimension],
        filter: Seq[VirtualAxis] => Boolean = _ => true
    ): Seq[Command] =
      crossCommand(
        cmd,
        alias = None,
        matrix = matrices,
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
        single(
          cmd = cmd,
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
      cmd,
      alias = Some(alias),
      matrix = matrices,
      dimensions = dimensions,
      filter = filter
    )

  }

  private def crossCommand(
      cmd: String,
      alias: Option[String],
      matrix: Seq[sbt.internal.ProjectMatrix],
      dimensions: Seq[Dimension],
      filter: Seq[VirtualAxis] => Boolean
  ): Seq[Command] = {
    val allProjects = matrix.flatMap(_.allProjects())

    val buckets = List.newBuilder[(Seq[String], Project)]

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

object Experimental extends Experimental

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
