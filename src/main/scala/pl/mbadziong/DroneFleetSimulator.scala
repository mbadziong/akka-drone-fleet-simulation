package pl.mbadziong

import akka.actor.typed.ActorSystem

object DroneFleetSimulator extends App {
  val droneOperator: ActorSystem[DroneOperator.Command] = ActorSystem(DroneOperator(), "DroneFleetSimulation")
  droneOperator ! DroneOperator.PrepareDroneFleet(5)
}
