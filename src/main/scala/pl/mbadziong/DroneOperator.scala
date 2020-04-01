package pl.mbadziong

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import pl.mbadziong.SimulationSupervisor._
import pl.mbadziong.airport.Airport
import pl.mbadziong.flight.{Flight, FlightCompleted, FlightDenied, FlightRequest, FlightResponse}

import scala.concurrent.duration._

object DroneOperator {

  def apply(operatorName: String, airport: Airport): Behavior[Command] =
    Behaviors.setup(context => new DroneOperator(context, operatorName, airport))

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
}

class DroneOperator(context: ActorContext[DroneOperator.Command], val name: String, val airport: Airport)
    extends AbstractBehavior[DroneOperator.Command](context) {
  import DroneOperator._
  import pl.mbadziong.Drone.BootDrone

  private val flightResponseAdapter = context.messageAdapter(WrappedFlightResponse.apply)

  private var droneIdToActor  = Map.empty[Long, ActorRef[Drone.Command]]
  private var flightIdToActor = Map.empty[Long, ActorRef[HandleFlightResponse]]
  private var idToFlight      = Map.empty[Long, Flight] //TODO: change to list
  private var nextDroneId     = 0

  context.log.info(s"drone operator $name with airport $airport created")

  override def onMessage(msg: DroneOperator.Command): Behavior[DroneOperator.Command] = msg match {
    case PrepareDroneFleet(dronesCount, replyTo) =>
      context.log.info(s"Initializing $dronesCount drones for operator $name")
      (1 to dronesCount) map (
          _ => {
            val droneNum = nextDroneId
            nextDroneId = nextDroneId + 1
            val droneActor = context.spawn(Drone(droneNum, name, airport), s"drone-$droneNum")
            context.watchWith(droneActor, DroneTerminated(droneActor, name, droneNum))
            droneIdToActor += droneNum.toLong -> droneActor
            droneActor
          }
      ) foreach (
        _ ! BootDrone
      )
      replyTo ! DroneFleetCreated(droneIdToActor)
      this
    case AddDroneToFleet(droneId, replyTo) =>
      droneIdToActor.get(droneId) match {
        case Some(droneActor) =>
          replyTo ! DroneAddedToFleet(droneActor)
        case None =>
          context.log.info(s"Drone $droneId has been assigned to operator $name")
          val droneActor = context.spawn(Drone(droneId, name, airport), s"drone-$droneId")
          context.watchWith(droneActor, DroneTerminated(droneActor, name, droneId))
          droneIdToActor += droneId -> droneActor
          replyTo ! DroneAddedToFleet(droneActor)
      }
      this
    case DroneTerminated(_, _, droneId) =>
      context.log.info(s"Drone $droneId of operator $name has been terminated")
      droneIdToActor -= droneId
      this
    case StopDroneFleet() =>
      Behaviors.stopped
    case RequestFleet(requestId, operatorName, replyTo) =>
      if (operatorName == name) {
        context.log.info(s"Operator $name owns $droneIdToActor")
        replyTo ! ReplyFleet(requestId, droneIdToActor.keySet)
        this
      } else {
        Behaviors.unhandled
      }
    case RequestFleetState(requestId, operator, replyTo) =>
      if (operator == name) {
        context.spawnAnonymous(FleetStateQuery(droneIdToActor, airport, requestId, replyTo, 3.seconds))
        this
      } else {
        Behaviors.unhandled
      }
    case HandleFly(flightRequest, replyTo) =>
      //TODO: create flight from flightrequest
      val flight = Flight(flightRequest.id, List(airport.position, flightRequest.destination, airport.position))
      flightIdToActor += flightRequest.id -> replyTo
      idToFlight += flightRequest.id      -> flight
      context.spawnAnonymous(FleetStateQuery(droneIdToActor, airport, flightRequest.id, context.self, 3.seconds))
      this
    case RespondFleetState(requestId, state) =>
      state.find { entry =>
        canHandleFlightRequest(entry._2)
      } match {
        case Some(entry) =>
          context.log.info(s"Drone ${entry._1} of operator $name will handle fly request $requestId")
          val droneRef      = droneIdToActor(entry._1)
          val flightRequest = idToFlight(requestId)
          droneRef ! Drone.Fly(flightRequest, flightResponseAdapter)
        case None =>
          context.log.info(s"Operator $name does not have any drone able to handle fly request $requestId")
      }
      idToFlight -= requestId
      this
    case WrappedFlightResponse(flightResponse) =>
      flightResponse match {
        case FlightCompleted(flightId) =>
          val replyTo = flightIdToActor(flightId)
          flightIdToActor -= flightId
          context.log.info(s"Flight $flightId completed")
          replyTo ! HandleFlightResponse(FlightCompleted(flightId))
        case FlightDenied(flightId, message) =>
          val replyTo = flightIdToActor(flightId)
          context.log.info(s"Flight $flightId denied. $message")
          flightIdToActor -= flightId
          replyTo ! HandleFlightResponse(FlightDenied(flightId, message))
      }
      this
  }

  private def canHandleFlightRequest(droneState: DroneState) = droneState match {
    case ReadyToFlight => true
    case _             => false
  }
}
