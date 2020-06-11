package pl.mbadziong.operator

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import pl.mbadziong.airport.Airport
import pl.mbadziong.drone.Drone
import pl.mbadziong.drone.Drone.BootDrone
import pl.mbadziong.flight._
import pl.mbadziong.query.FleetStateQuery
import pl.mbadziong.route.RouteProvider
import pl.mbadziong.route.RouteProvider.{RouteRequest, RouteResponse}
import pl.mbadziong.supervisor.SimulationSupervisor
import pl.mbadziong.supervisor.SimulationSupervisor._

import scala.concurrent.duration._

object DroneOperator {

  def apply(operatorName: String, airport: Airport): Behavior[Command] =
    Behaviors.setup(_ => droneOperator(operatorName, airport, Map.empty, Map.empty, Map.empty, Map.empty, Map.empty, 0))

  trait Command
  final case class PrepareDroneFleet(dronesCount: Int, replyTo: ActorRef[DroneFleetCreated]) extends Command
  final case class AddDroneToFleet(droneId: Long, replyTo: ActorRef[DroneAddedToFleet])      extends Command
  final case class DroneAddedToFleet(drone: ActorRef[Drone.Command])
  final case class StopDroneFleet() extends Command
  final case class RequestFleet(requestId: Long, operator: String, replyTo: ActorRef[ReplyFleet])
      extends Command
      with SimulationSupervisor.Command
  final case class ReplyFleet(requestId: Long, ids: Set[Long])
  final case class DroneTerminated(drone: ActorRef[Drone.Command], operatorName: String, droneId: Long) extends Command
  final case class HandleFly(flight: FlightRequest, replyTo: ActorRef[HandleFlightResponse])            extends Command
  final case class WrappedFlightResponse(flightResponse: FlightResponse)                                extends Command

  def droneOperator(name: String,
                    airport: Airport,
                    droneIdToActor: Map[Long, ActorRef[Drone.Command]],
                    idToFlightRequest: Map[Long, FlightRequest],
                    flightIdToActor: Map[Long, ActorRef[HandleFlightResponse]],
                    flightIdToDrone: Map[Long, ActorRef[Drone.Command]],
                    flightIdToResponse: Map[Long, FlightResponse],
                    nextDroneId: Long): Behavior[Command] = {
    Behaviors.receive { (context, message) =>
      message match {
        case PrepareDroneFleet(dronesCount, replyTo) =>
          context.log.info(s"Initializing $dronesCount drones for operator $name")
          var tempNextId = nextDroneId
          val newFleet = (1 to dronesCount) map (
              _ => {
                val droneActor = context.spawn(Drone(tempNextId, name, airport), s"drone-$tempNextId")
                context.watchWith(droneActor, DroneTerminated(droneActor, name, tempNextId))
                droneActor ! BootDrone
                val idToActor = tempNextId -> droneActor
                tempNextId = tempNextId + 1
                idToActor
              }
          )
          val newFleetMap = droneIdToActor ++ newFleet
          replyTo ! DroneFleetCreated(newFleetMap)
          droneOperator(name, airport, newFleetMap, idToFlightRequest, flightIdToActor, flightIdToDrone, flightIdToResponse, tempNextId)

        case AddDroneToFleet(droneId, replyTo) =>
          val newDroneIdToActor = droneIdToActor.get(droneId) match {
            case Some(droneActor) =>
              replyTo ! DroneAddedToFleet(droneActor)
              droneIdToActor
            case None =>
              context.log.info(s"Drone $droneId has been assigned to operator $name")
              val droneActor = context.spawn(Drone(droneId, name, airport), s"drone-$droneId")
              context.watchWith(droneActor, DroneTerminated(droneActor, name, droneId))
              replyTo ! DroneAddedToFleet(droneActor)
              droneIdToActor + (droneId -> droneActor)
          }
          droneOperator(name,
                        airport,
                        newDroneIdToActor,
                        idToFlightRequest,
                        flightIdToActor,
                        flightIdToDrone,
                        flightIdToResponse,
                        nextDroneId)

        case DroneTerminated(_, _, droneId) =>
          context.log.info(s"Drone $droneId of operator $name has been terminated")
          droneOperator(name,
                        airport,
                        droneIdToActor - droneId,
                        idToFlightRequest,
                        flightIdToActor,
                        flightIdToDrone,
                        flightIdToResponse,
                        nextDroneId)

        case StopDroneFleet() =>
          Behaviors.stopped

        case RequestFleet(requestId, operatorName, replyTo) =>
          if (operatorName == name) {
            context.log.info(s"Operator $name owns $droneIdToActor")
            replyTo ! ReplyFleet(requestId, droneIdToActor.keySet)
          }
          Behaviors.same

        case RequestFleetState(requestId, operator, replyTo) =>
          if (operator == name) {
            context.spawnAnonymous(FleetStateQuery(droneIdToActor, airport, requestId, replyTo, 3.seconds))
          }
          droneOperator(name, airport, droneIdToActor, idToFlightRequest, flightIdToActor, flightIdToDrone, flightIdToResponse, nextDroneId)

        case HandleFly(flightRequest, replyTo) =>
          context.spawnAnonymous(FleetStateQuery(droneIdToActor, airport, flightRequest.id, context.self, 3.seconds))
          droneOperator(
            name,
            airport,
            droneIdToActor,
            idToFlightRequest + (flightRequest.id -> flightRequest),
            flightIdToActor + (flightRequest.id   -> replyTo),
            flightIdToDrone,
            flightIdToResponse,
            nextDroneId
          )

        case RespondFleetState(requestId, state) =>
          state.find { entry =>
            canHandleFlightRequest(entry._2)
          } match {
            case Some(entry) =>
              context.log.info(s"Drone ${entry._1} of operator $name will handle fly request $requestId")
              val flightRequest = idToFlightRequest(requestId)
              val replyTo       = flightIdToActor(flightRequest.id)
              context.spawnAnonymous(RouteProvider()) ! RouteRequest(requestId,
                                                                     airport.position,
                                                                     flightRequest.destination,
                                                                     10,
                                                                     context.self)
              replyTo ! HandleFlightResponse(FlightAccepted(flightRequest.id))
              droneOperator(
                name,
                airport,
                droneIdToActor,
                idToFlightRequest + (flightRequest.id -> flightRequest),
                flightIdToActor - flightRequest.id,
                flightIdToDrone + (requestId -> droneIdToActor(entry._1)),
                flightIdToResponse,
                nextDroneId
              )

            case None =>
              val msg = s"Operator $name does not have any drone able to handle fly request $requestId"
              context.log.info(msg)
              val flightRequest = idToFlightRequest(requestId)
              flightIdToActor(flightRequest.id) ! HandleFlightResponse(FlightDenied(flightRequest.id, msg))
              context.self ! WrappedFlightResponse(FlightDenied(requestId, msg))
              droneOperator(
                name,
                airport,
                droneIdToActor,
                idToFlightRequest,
                flightIdToActor - flightRequest.id,
                flightIdToDrone,
                flightIdToResponse,
                nextDroneId
              )
          }

        case RouteResponse(requestId, route) =>
          val flightResponseAdapter = context.messageAdapter(WrappedFlightResponse.apply)
          val droneRef              = flightIdToDrone(requestId)
          val flight                = Flight(requestId, route)
          droneRef ! Drone.Fly(flight, flightResponseAdapter)
          droneOperator(
            name,
            airport,
            droneIdToActor,
            idToFlightRequest - requestId,
            flightIdToActor,
            flightIdToDrone - requestId,
            flightIdToResponse,
            nextDroneId
          )

        case WrappedFlightResponse(flightResponse) =>
          val flightStatus = flightResponse match {
            case FlightCompleted(flightId) =>
              context.log.info(s"Flight $flightId completed")
              flightId -> FlightCompleted(flightId)
            case FlightDenied(flightId, message) =>
              context.log.info(s"Flight $flightId denied. $message")
              flightId -> FlightDenied(flightId, message)
          }
          droneOperator(name,
                        airport,
                        droneIdToActor,
                        idToFlightRequest,
                        flightIdToActor,
                        flightIdToDrone,
                        flightIdToResponse + flightStatus,
                        nextDroneId)
      }
    }
  }

  private def canHandleFlightRequest(droneState: DroneState) = droneState match {
    case ReadyToFlight => true
    case _             => false
  }
}
