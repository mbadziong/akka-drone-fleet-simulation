package pl.mbadziong

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import pl.mbadziong.DroneOperator.{HandleFly, PrepareDroneFleet, ReplyFleet, RequestFleet}
import pl.mbadziong.airport.Airport
import pl.mbadziong.drone.Position
import pl.mbadziong.flight.{FlightAccepted, FlightDenied, FlightRequest, FlightResponse}

object SimulationSupervisor {
  def apply(): Behavior[Command] =
    supervisor(Map.empty)

  trait Command
  final case class CreateDroneOperator(name: String, airport: Airport, replyTo: ActorRef[CreatedDroneOperator]) extends Command
  final case class CreatedDroneOperator(droneOperator: ActorRef[DroneOperator.Command])
  final case class GenerateDrones(name: String, count: Int, replyTo: ActorRef[DroneFleetCreated]) extends Command
  final case class DroneFleetCreated(droneActors: Map[Long, ActorRef[Drone.Command]])             extends Command
  final case class OperatorTerminated(operator: String)                                           extends Command
  final case class HandleFlightRequest(flightRequest: FlightRequest, operator: String, replyTo: ActorRef[HandleFlightResponse])
      extends Command
  final case class HandleFlightResponse(flightResponse: FlightResponse)
  final case class FlightRequestResult(flightResponse: FlightResponse) extends Command
  final case class RequestFleetState(requestId: Long, operator: String, replyTo: ActorRef[RespondFleetState])
      extends Command
      with DroneOperator.Command
  final case class RespondFleetState(requestId: Long, state: Map[Long, DroneState]) extends DroneOperator.Command

  sealed trait DroneState
  final case class InFlight(position: Position) extends DroneState
  case object ReadyToFlight                     extends DroneState
  case object LoadsBattery                      extends DroneState
  case object TimedOut                          extends DroneState

  private def supervisor(operatorNameToActor: Map[String, ActorRef[DroneOperator.Command]]): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match {
        case CreateDroneOperator(name, airport, replyTo) =>
          val droneOperator = context.spawn(DroneOperator(name, airport), "name")
          replyTo ! CreatedDroneOperator(droneOperator)
          context.watchWith(droneOperator, OperatorTerminated(name))
          supervisor(operatorNameToActor + (name -> droneOperator))
        case GenerateDrones(name, count, replyTo) =>
          val droneOperator = operatorNameToActor(name)
          droneOperator ! PrepareDroneFleet(count, replyTo)
          supervisor(operatorNameToActor)
        case req @ RequestFleet(requestId, operator, replyTo) =>
          operatorNameToActor.get(operator) match {
            case Some(ref) =>
              ref ! req
            case None =>
              replyTo ! ReplyFleet(requestId, Set.empty)
          }
          supervisor(operatorNameToActor)
        case req @ RequestFleetState(requestId, operator, replyTo) =>
          operatorNameToActor.get(operator) match {
            case Some(ref) =>
              ref ! req
            case None =>
              replyTo ! RespondFleetState(requestId, Map.empty)
          }
          supervisor(operatorNameToActor)
        case HandleFlightRequest(flightRequest, operator, replyTo) =>
          operatorNameToActor.get(operator) match {
            case Some(ref) =>
              ref ! HandleFly(flightRequest)
              replyTo ! HandleFlightResponse(FlightAccepted(flightRequest.id))
            case None =>
              replyTo ! HandleFlightResponse(FlightDenied(flightRequest.id, "operator not found"))
          }
          supervisor(operatorNameToActor)
        case OperatorTerminated(operatorName) =>
          context.log.info(s"Drone fleet for operator $operatorName has been terminated")
          supervisor(operatorNameToActor - operatorName)
        case _ => Behaviors.unhandled
      }
    }
}
