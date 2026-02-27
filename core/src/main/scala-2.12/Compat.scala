package commandmatrix

private[commandmatrix] object Compat {
  type ProjectMatrix = sbt.internal.ProjectMatrix
  val Requirement: sbt.Plugins = sbtprojectmatrix.ProjectMatrixPlugin

  val ReflectionUtil = sbtprojectmatrix.ReflectionUtil
}
