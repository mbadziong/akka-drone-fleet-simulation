package pl.mbadziong

import akka.actor.typed.ActorSystem

object DroneFleetSimulator extends App {
  val droneSimulationSupervisor: ActorSystem[SimulationSupervisor.Command] =
    ActorSystem(SimulationSupervisor(), "DroneFleetSimulation")
}
