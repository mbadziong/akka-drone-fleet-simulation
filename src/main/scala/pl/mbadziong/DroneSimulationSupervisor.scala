package pl.mbadziong

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import pl.mbadziong.DroneSimulationSupervisor._
import pl.mbadziong.drone.Position

object DroneSimulationSupervisor {
  def apply(): Behavior[Command] =
    Behaviors.setup[Command](context => new DroneSimulationSupervisor(context))

  sealed trait Command
  final case class CreateDroneOperator(name: String, replyTo: ActorRef[CreatedDroneOperator]) extends Command
  final case class CreatedDroneOperator(droneOperator: ActorRef[DroneOperator.Command])
  final case class DroneFleetCreated(droneActors: Map[Long, ActorRef[Drone.Command]]) extends Command
  final case class OperatorTerminated(operator: String)                              extends Command

  final case class RequestFleet(requestId: Long, operator: String, replyTo: ActorRef[ReplyFleet]) extends Command with DroneOperator.Command
  final case class ReplyFleet(requestId: Long, ids: Set[String])

  final case class RequestFleetState(requestId: Long, operator: String, replyTo: ActorRef[RespondFleetState]) extends Command with DroneOperator.Command
  final case class RespondFleetState(requestId: Long, state: Map[Long, DroneState])

  sealed trait DroneState
  final case class InFlight(position: Position) extends DroneState
  case object ReadyToFlight                     extends DroneState
  case object LoadsBattery                      extends DroneState
  case object TimedOut                          extends DroneState
}

class DroneSimulationSupervisor(context: ActorContext[Command]) extends AbstractBehavior[Command](context) {
  context.log.info("Drone fleet simulator App started")

  var operatorNameToActor = Map.empty[String, ActorRef[DroneOperator.Command]]

  override def onMessage(msg: Command): Behavior[Command] = msg match {
    case CreateDroneOperator(name, replyTo) =>
      val droneOperator = context.spawn(DroneOperator(name), "name")
      replyTo ! CreatedDroneOperator(droneOperator)
      context.watchWith(droneOperator, OperatorTerminated(name))
      operatorNameToActor += name -> droneOperator
      this
    case req @ RequestFleet(requestId, operator, replyTo) =>
      operatorNameToActor.get(operator) match {
        case Some(ref) =>
          ref ! req
        case None =>
          replyTo ! ReplyFleet(requestId, Set.empty)
      }
      this
    case req @ RequestFleetState(requestId, operator, replyTo) =>
      operatorNameToActor.get(operator) match {
        case Some(ref) =>
          ref ! req
        case None =>
          replyTo ! RespondFleetState(requestId, Map.empty)
      }
      this
    case OperatorTerminated(operatorName) =>
      context.log.info(s"Drone fleet for operator $operatorName has been terminated")
      operatorNameToActor -= operatorName
      this
    case _ => Behaviors.unhandled
  }

  override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
    case PostStop =>
      context.log.info("Drone fleet simulator App stopped")
      this
  }
}
