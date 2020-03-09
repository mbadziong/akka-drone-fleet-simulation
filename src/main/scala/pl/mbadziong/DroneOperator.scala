package pl.mbadziong

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import pl.mbadziong.Drone.RespondState
import pl.mbadziong.FleetStateQuery.{Command, WrappedRespondState}
import pl.mbadziong.SimulationSupervisor._
import pl.mbadziong.airport.Airport
import pl.mbadziong.flight.{FlightRequest, FlightResponse}

import scala.concurrent.duration._

object DroneOperator {

  def apply(operatorName: String, airport: Airport): Behavior[Command] =
    Behaviors.setup(context => new DroneOperator(context, operatorName, airport))

  trait Command
  final case class PrepareDroneFleet(dronesCount: Int, replyTo: ActorRef[DroneFleetCreated]) extends Command
  final case class AddDroneToFleet(droneId: Long, replyTo: ActorRef[DroneAddedToFleet])      extends Command
  final case class DroneAddedToFleet(drone: ActorRef[Drone.Command])
  final case class StopDroneFleet()                                                                               extends Command
  final case class RequestOwnedDrones(requestId: Long, operatorName: String, replyTo: ActorRef[ReplyOwnedDrones]) extends Command
  final case class ReplyOwnedDrones(requestId: Long, ids: Set[Long])
  final case class DroneTerminated(drone: ActorRef[Drone.Command], operatorName: String, droneId: Long) extends Command

  final case class FlightCompleted(flightId: Long)                                              extends Command
  final case class FlightDenied(flightId: Long)                                                 extends Command
  final case class Fly(flightRequest: FlightRequest, replyTo: ActorRef[Command]) extends Command
  final case class WrappedFlightResponse(flightResponse: FlightResponse) extends Command
}

class DroneOperator(context: ActorContext[DroneOperator.Command], val name: String, val airport: Airport)
    extends AbstractBehavior[DroneOperator.Command](context) {
  import DroneOperator._
  import pl.mbadziong.Drone.BootDrone

  private val flightResponseAdapter = context.messageAdapter(WrappedFlightResponse.apply)

  private var droneIdToActor    = Map.empty[Long, ActorRef[Drone.Command]]
  private var flightIdToActor   = Map.empty[Long, ActorRef[WrappedFlightResponse]]
  private var flightIdToRequest = Map.empty[Long, FlightRequest]

  context.log.info(s"drone operator $name with airport $airport created")

  override def onMessage(msg: DroneOperator.Command): Behavior[DroneOperator.Command] = msg match {
    case PrepareDroneFleet(dronesCount, replyTo) =>
      context.log.info(s"Initializing $dronesCount drones for operator $name")
      (1 to dronesCount) map (
          droneNum => {
            val droneActor = context.spawn(Drone(droneNum, name), s"drone-$droneNum")
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
          val droneActor = context.spawn(Drone(droneId, name), s"drone-$droneId")
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
    case RequestOwnedDrones(requestId, operatorName, replyTo) =>
      if (operatorName == name) {
        context.log.info(s"Operator $name owns $droneIdToActor")
        replyTo ! ReplyOwnedDrones(requestId, droneIdToActor.keySet)
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
    case Fly(flightRequest, replyTo) =>
      flightIdToActor += flightRequest.id.toLong   -> replyTo
      flightIdToRequest += flightRequest.id.toLong -> flightRequest
      context.spawnAnonymous(FleetStateQuery(droneIdToActor, airport, flightRequest.id, context.self, 3.seconds))
      this
    case RespondFleetState(requestId, state) =>
      val replyTo = flightIdToActor(requestId)
      state.find { entry =>
        canHandleFlightRequest(entry._2)
      } match {
        case Some(entry) =>
          val droneRef      = droneIdToActor(entry._1)
          val flightRequest = flightIdToRequest(entry._1)
          droneRef ! Drone.Fly(flightRequest, flightResponseAdapter)
          flightIdToActor -= requestId
          this
      }
    case WrappedFlightResponse(flightResponse) =>
      flightResponse match {
        case
      }

  }

  private def canHandleFlightRequest(droneState: DroneState) = droneState match {
    case ReadyToFlight => true
    case _             => false
  }

  override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
    case PostStop =>
      context.log.info(s"drone operator $name stopped")
      this
  }
}
