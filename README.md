## sbt-commandmatrix

Define SBT commands to run subsets of [sbt-projectmatrix](https://github.com/sbt/sbt-projectmatrix) build definition.

### Status (May 2021)

Active, super experimental, any usage will be punished by weird errors and unpredictable behaviour.


### Motivation

It's not uncommon to encounter projects that publish for:

1. multiple platforms, such as Scala Native, Scala.js and JVM
2. multiple Scala versions, e.g. 2.13, 2.12, 3.0.x
3. multiple custom axis - i.e. different versions of some dependency

Subjectively, `sbt-projectmatrix` solves this problem really, really well. Projects such as [Weaver Test](https://github.com/disneystreaming/weaver-test/blob/master/build.sbt) and [Sttp](https://github.com/softwaremill/sttp/blob/master/build.sbt) manage huge project matrices with all of the variations listed above.

Issues start when it comes to CI: because projectmatrix generates a project per set of axis values, you can't use `++2.13.5` style of cross building.

This limits the ability to parallelise jobs on CI, where matrix builds became the norm a while ago - CIs such as Travis and Github Actions support it.

### Installation

![Maven Central](https://index.scala-lang.org/indoorvivants/sbt-commandmatrix/latest.svg?color=orange)

This plugin only works with sbt-projectmatrix, so in your `project/plugins.sbt` you should have:

```scala
addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.8.0")
addSbtPlugin("com.indoorvivants" % "sbt-commandmatrix" % "<VERSION>")
```

Where the version can be picked up from the badge above.

## Proposal and Usage

This plugin generates SBT [commands](https://www.scala-sbt.org/1.x/docs/Commands.html#Commands) based on some minimal information provided by the user.

Let's consider an example:

```scala
import commandmatrix._

lazy val scala212 = "2.12.13"
lazy val scala213 = "2.13.5"

lazy val core = projectMatrix
  .in(file("core"))
  .jvmPlatform(Seq(scala213, scala212))
  .jsPlatform(Seq(scala212))
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
* `test-2_12-js`

And run the `test` command in appropriate projects.




