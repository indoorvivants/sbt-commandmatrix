import cats.effect._

object Hello extends IOApp {
  override def run(args: List[String]) = 
    IO(println("hello!")).map(_ => ExitCode.Success)
}
