# sbt-commandmatrix

- [sbt-commandmatrix](#sbt-commandmatrix)
    - [Installation](#installation)
    - [Status (September 2021)](#status-september-2021)
  - [Define commands to run matrix subsets](#define-commands-to-run-matrix-subsets)
    - [Motivation](#motivation)
    - [Proposal and Usage](#proposal-and-usage)
    - [Aggregate commands and stubbing](#aggregate-commands-and-stubbing)
  - [Whole matrix definition](#whole-matrix-definition)
    - [Motivation](#motivation-1)
    - [Proposition and Usage](#proposition-and-usage)

This plugin extends [sbt-projectmatrix](https://github.com/sbt/sbt-projectmatrix) plugin with two additional features:

1. [Define SBT commands to run subsets of the
   matrix](#define-commands-to-run-matrix-subsets), i.e. turning single
   `test` command into `test-2_13-jvm`, `test-2_13-js`, etc., depending
   on what dimensions your matrix has

2. [A helper method to define the matrix](#whole-matrix-definition), configuring and controlling the holes

### Installation

![Maven Central](https://index.scala-lang.org/indoorvivants/sbt-commandmatrix/latest.svg?color=orange)

This plugin only works with sbt-projectmatrix, so in your `project/plugins.sbt` you should have:

```scala
addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.8.0")
addSbtPlugin("com.indoorvivants" % "sbt-commandmatrix" % "<VERSION>")
```

Where the version can be picked up from the badge above.

### Status (September 2021)

Active, super experimental, any usage will be punished by weird errors and unpredictable behaviour.

## Define commands to run matrix subsets

### Motivation

It's not uncommon to encounter projects that publish for:

1. multiple platforms, such as Scala Native, Scala.js and JVM
2. multiple Scala versions, e.g. 2.13, 2.12, 3.0.x
3. multiple custom axis - i.e. different versions of some dependency

Subjectively, `sbt-projectmatrix` solves this problem really, really well. Projects such as [Weaver Test](https://github.com/disneystreaming/weaver-test/blob/master/build.sbt) and [Sttp](https://github.com/softwaremill/sttp/blob/master/build.sbt) manage huge project matrices with all of the variations listed above.

Issues start when it comes to CI: because projectmatrix generates a project per set of axis values, you can't use `++2.13.5` style of cross building.

This limits the ability to parallelise jobs on CI, where matrix builds became the norm a while ago - CIs such as Travis and Github Actions support it.

### Proposal and Usage

A more involved demonstration of techniques below is in the example:

* [build.sbt](https://github.com/indoorvivants/sbt-commandmatrix/blob/master/example/build.sbt)

* [Github Actions
   workflow](https://github.com/indoorvivants/sbt-commandmatrix/blob/master/.github/workflows/example.yml)

* [Sample matrix build](https://github.com/indoorvivants/sbt-commandmatrix/actions/runs/822941619)

---

This plugin generates SBT [commands](https://www.scala-sbt.org/1.x/docs/Commands.html#Commands) based on some minimal information provided by the user.

Let's consider an example:

```scala
import commandmatrix._

lazy val scala212 = "2.12.13"
lazy val scala213 = "2.13.5"
lazy val scala3   = "3.0.0-RC3"

lazy val core = projectMatrix
  .in(file("core"))
  .jvmPlatform(Seq(scala3, scala213, scala212))
  .jsPlatform(Seq(scala3, scala212))
```

Here our `core` projectmatrix defines several projects (2 for JVM, 1 for JavaScript). 

Our goal is to run the `test` command on subsets of this matrix:

```scala
inThisBuild(
  Seq(
    commands ++= CrossCommand.single(
      "test",
      matrices = Seq(core),
      dimensions = Seq(
        Dimension.scala("2.12"), // "2.12" is the default one
        Dimension.platform()
      )
    )
)
```

Which will generate the following commands in the build:

* `test-2_12-jvm`
* `test-2_13-jvm`
* `test-3.0.0-RC3-jvm`
* `test-2_12-js`

And run the `test` command in appropriate projects.

### Aggregate commands and stubbing

We often want to run a list of commands, but some of those commands
might not be available for particular platform/scala version combination.

Common examples I've seen:

1. Running versions of scalafmt/scalafix that don't support Scala 3.
2. Running `undeclaredCompileDependencies` tasks from sbt-explicit-dependencies
plugin on Scala.js project produces false positives and breaks the build.


In this case, you can use `CrossCommand.composite` like this:

```scala
    commands ++= CrossCommand.composite(
      "codeQuality",
      Seq(
        "scalafmtCheckAll",
        "unusedCompileDependenciesTest",
        "undeclaredCompileDependenciesTest"
      ),
      matrices = Seq(core),
      dimensions = Seq(
        Dimension.scala("2.12", fullFor3 = true),
        Dimension.platform()
      ),
      filter = axes => // 1
        CrossCommand.filter.notScala3(axes) &&
          CrossCommand.filter.onlyJvm(axes),
      stubMissing = true // 2
    )
  )
```

* [1]: only consider JVM projects not on Scala 3

* [2]: for all the filtered out project, produce an empty command

Because of stubbing, running `codeQuality-3.0.0-RC3-jvm` will succeed without
doing
anything (same for `codeQuality-2_12-js`, filtered out because it's non-JVM).

Having this allows us to define a signifcantly simpler Github Actions workflow:

```yaml
name: Example
on:
  push:
    branches: ["master"]
  pull_request:
    branches: ["*"]

jobs:
  example:
    name: Example ${{matrix.scalaVersion}} (${{matrix.scalaPlatform}})
    strategy:
      fail-fast: false
      matrix:
        os: [ubuntu-latest]
        java: [adopt@1.8]
        scalaVersion: ["2_12", "2_13", "3_0_0-RC3"]
        scalaPlatform: ["jvm", "js"]
    runs-on: ${{ matrix.os }}
    env:
      BUILD_KEY: ${{matrix.scalaVersion}}-${{matrix.scalaPlatform}}
    steps:
      - name: Checkout current branch
        uses: actions/checkout@v2

      - name: Setup Java and Scala
        uses: olafurpg/setup-scala@v10
        with:
          java-version: ${{ matrix.java }}

      - name: Run code quality
        run: |
          sbt codeQuality-$BUILD_KEY
```

## Whole matrix definition

### Motivation

Similar to the matrix commands, we want to deal with the fact that not all "cells" in our matrix have the same configuration. For example:

1. If we don't have platform-specific code, then we don't want to run Scalafmt on platforms other than JVM (same for Scala versions)
  
2. Some plugins don't support Scala 3 at all, and as such it's better to just disable them, which is not a settingm but rather a modification of the `Project` in SBT (for example, Scalafix is still experimental with Scala 3)

3. Some combinations of virtual axes just shouldn't exist, for example Scala Native is not yet available for Scala 3, and we want to avoid creating projects for this combination.

Let's consider an example.

Say we have a project that is built for 

1. Scala versions `2.12, 2.13, 3.0.2`
2. Platforms `jvm, js, native`
3. Some library (which is our main dependency), versions `ver1, ver2`
   1. An example of this may be Cats Effect, where series 2.x and 3.x are not binary compatible, so libraries that depend on them either have to choose, or cross-build

In this relatively simple setup (and it is simple, by Scala ecosystem standards :D) we now have the following combinations:

```scala
  ("2.12", "jvm", "ver1"),
  ("2.12", "jvm", "ver2"),
  ("2.12", "js", "ver1"),
  ("2.12", "js", "ver2"),
  ("2.12", "native", "ver1"),
  ("2.12", "native", "ver2"),
  ("2.13", "jvm", "ver1"),
  ("2.13", "jvm", "ver2"),
  ("2.13", "js", "ver1"),
  ("2.13", "js", "ver2"),
  ("2.13", "native", "ver1"),
  ("2.13", "native", "ver2"),
  ("3.0.2", "jvm", "ver1"),
  ("3.0.2", "jvm", "ver2"),
  ("3.0.2", "js", "ver1"),
  ("3.0.2", "js", "ver2"),
  ("3.0.2", "native", "ver1"),
  ("3.0.2", "native", "ver2")
```

The goal of this project is to make generating those combinations as simple as possible, and **then** poking holes in the matrix, for example removing the whole `Scala 3 + Scala Native` subset, because it just doesn't exist.

### Proposition and Usage

To gain access to this functionality, just import everything from the `commandmatrix.extra` package in your `build.sbt`:

```scala
import commandmatrix.extra._
```

And that's it. What we propose, is that first we define the entire matrix (the matrix is dense, i.e. most cells are present), and then we refine it, by conditionally removing/keeping/configuring rows in it.

Let's say, that for example above, we want to do the following things:

1. **Completely remove** the `Scala 3 + Scala Native` combination
2. **Disable** Scalafix plugin for all Scala 3 projects
3. **Not publish** any of the Scala 2 projects on Scala.js

First, let's define the special axis for our imaginary library dependency.

**project/LibraryVersionAxis.scala**

```scala
import sbt.VirtualAxis

sealed abstract class LibraryAxis(
    val idSuffix: String,
    val directorySuffix: String
) extends VirtualAxis.WeakAxis

object LibraryAxis {
  case object V1 extends LibraryAxis("-V1", "-v1")
  case object V2 extends LibraryAxis("-V2", "-v2")
}
```

This will allow us to identify projects generated for distinct versions of this imaginary dependency.

Now, in our **build.sbt** we can first define the whole matrix, and then conditionally define omissions/changes to desired cells:

**build.sbt**
```scala
lazy val core = 
  projectMatrix
    .in(file("."))
    .someVariations(
      List(scala212, scala213, scala3),
      List(VirtualAxis.jvm, VirtualAxis.js, VirtualAxis.native),
      List(LibraryAxis.V1, LibraryAxis.V2)
    ) {
      // 1: Completely remove the `Scala 3 + Scala Native` combination
      case (scalaV, axes)
          if scalaV.isScala3 && axes.contains(VirtualAxis.native) =>
        MatrixAction.Skip

      // 2: Disable Scalafix plugin for all Scala 3 projects
      case (scalaV, _) if scalaV.isScala3 =>
        MatrixAction.Configure(_.disablePlugins(ScalafixPlugin))

      // 3: Not publish any of the Scala 2 projects on Scala.js
      case (scalaV, axes) if !scalaV.isScala3 && axes.contains(VirtualAxis.js) =>
        MatrixAction.Settings(
          Seq(
            publish / skip := true,
            publishLocal / skip := true
          )
        )
    }
```

**(See the version of this snippet in the [test file](core/src/sbt-test/commandMatrix/extra/build.sbt) which is verified on CI and is guaranteed to be correct)**

If you want to generate the full matrix without any holes, you can use the `allVariations` method:

```scala
lazy val core = 
  projectMatrix
    .in(file("."))
    .allVariations(
      List(scala212, scala213, scala3),
      List(VirtualAxis.jvm, VirtualAxis.js, VirtualAxis.native),
      List(LibraryAxis.V1, LibraryAxis.V2)
    )
```