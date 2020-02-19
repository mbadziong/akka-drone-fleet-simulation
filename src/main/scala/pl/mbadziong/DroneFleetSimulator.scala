package pl.mbadziong

import akka.actor.typed.ActorSystem

object DroneFleetSimulator extends App {
  val droneSimulationSupervisor: ActorSystem[DroneSimulationSupervisor.Command] =
    ActorSystem(DroneSimulationSupervisor(), "DroneFleetSimulation")

  droneSimulationSupervisor ! DroneSimulationSupervisor.CreateDroneOperator("Mateusz")
}
