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

import scala.reflect.ClassTag
import scala.util.Try
import java.lang.reflect.InvocationTargetException

private[commandmatrix] object Compat {
  type ProjectMatrix = sbt.ProjectMatrix
  val Requirement: sbt.Plugins = sbt.Plugins.empty

  object ReflectionUtil:
      def getSingletonObject[A: ClassTag](classLoader: ClassLoader, className: String): Try[A] =
        Try {
          val clazz = classLoader.loadClass(className)
          val t = implicitly[ClassTag[A]].runtimeClass
          Option(clazz.getField("MODULE$").get(null)) match {
            case None =>
              throw new ClassNotFoundException(
                s"Unable to find $className using classloader: $classLoader"
              )
            case Some(c) if !t.isInstance(c) =>
              throw new ClassCastException(s"${clazz.getName} is not a subtype of $t")
            case Some(c) => c.asInstanceOf[A]
          }
        }
          .recover {
            case i: InvocationTargetException if i.getTargetException != null =>
              throw i.getTargetException
          }

      def objectExists(classLoader: ClassLoader, className: String): Boolean =
        try {
          classLoader.loadClass(className).getField("MODULE$").get(null) != null
        } catch {
          case _: Throwable => false
        }

      def withContextClassloader[A](loader: ClassLoader)(body: ClassLoader => A): A = {
        val current = Thread.currentThread().getContextClassLoader
        try {
          Thread.currentThread().setContextClassLoader(loader)
          body(loader)
        } finally Thread.currentThread().setContextClassLoader(current)
      }
    end ReflectionUtil
}
