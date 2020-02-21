package pl.mbadziong

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import pl.mbadziong.DroneSimulationSupervisor.{Command, CreateDroneOperator, CreatedDroneOperator}

object DroneSimulationSupervisor {
  def apply(): Behavior[Command] =
    Behaviors.setup[Command](context => new DroneSimulationSupervisor(context))

  sealed trait Command
  final case class CreateDroneOperator(name: String, replyTo: ActorRef[CreatedDroneOperator]) extends Command
  final case class CreatedDroneOperator(droneOperator: ActorRef[DroneOperator.Command])
  final case class DroneFleetCreated(droneActors: Map[Int, ActorRef[Drone.Command]]) extends Command
}

class DroneSimulationSupervisor(context: ActorContext[Command]) extends AbstractBehavior[Command](context) {
  context.log.info("Drone fleet simulator App started")

  override def onMessage(msg: Command): Behavior[Command] = msg match {
    case CreateDroneOperator(name, replyTo) =>
      val droneOperator = context.spawn(DroneOperator(name), "name")
      replyTo ! CreatedDroneOperator(droneOperator)
      this
    case _ => Behaviors.unhandled
  }

  override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
    case PostStop =>
      context.log.info("Drone fleet simulator App stopped")
      this
  }
}
