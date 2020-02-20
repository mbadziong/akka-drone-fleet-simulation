package pl.mbadziong

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{Behavior, PostStop, Signal}
import pl.mbadziong.DroneOperator.PrepareDroneFleet
import pl.mbadziong.DroneSimulationSupervisor.{Command, CreateDroneOperator}

object DroneSimulationSupervisor {
  def apply(): Behavior[Command] =
    Behaviors.setup[Command](context => new DroneSimulationSupervisor(context))

  sealed trait Command
  final case class CreateDroneOperator(name: String) extends Command
}

class DroneSimulationSupervisor(context: ActorContext[Command]) extends AbstractBehavior[Command](context) {
  context.log.info("Drone fleet simulator App started")

  override def onMessage(msg: Command): Behavior[Command] = msg match {
    case CreateDroneOperator(name) =>
      val droneOperator = context.spawn(DroneOperator(name), "name")
      droneOperator ! PrepareDroneFleet(5)
      this
  }

  override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
    case PostStop =>
      context.log.info("Drone fleet simulator App stopped")
      this
  }
}
