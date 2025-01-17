package pl.mbadziong

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import pl.mbadziong.http.SupervisorRoutes
import pl.mbadziong.supervisor.SimulationSupervisor

import scala.util.Failure
import scala.util.Success

object DroneFleetSimulator {

  private val PORT = "simulator.server.port"

  private def startHttpServer(routes: Route, system: ActorSystem[_]): Unit = {
    // Akka HTTP still needs a classic ActorSystem to start
    implicit val classicSystem: akka.actor.ActorSystem = system.toClassic
    import system.executionContext

    val port          = system.settings.config.getInt(PORT)
    val futureBinding = Http().bindAndHandle(routes, "localhost", port)
    futureBinding.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info("Server online at http://{}:{}/", address.getHostString, address.getPort)
      case Failure(ex) =>
        system.log.error("Failed to bind HTTP endpoint, terminating system", ex)
        system.terminate()
    }
  }

  def main(args: Array[String]): Unit = {
    val simulator = Behaviors.setup[Nothing] { context =>
      val droneSimulationSupervisor = context.spawn(SimulationSupervisor(), "Simulation")
      context.watch(droneSimulationSupervisor)

      val routes = new SupervisorRoutes(droneSimulationSupervisor)(context.system)
      startHttpServer(routes.supervisorRoutes, context.system)

      Behaviors.empty
    }
    val system = ActorSystem[Nothing](simulator, "SimulatorHttpServer")
  }
}
