package pl.mbadziong

import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import pl.mbadziong.drone.Position
import pl.mbadziong.flight.{FlightRequest, FlightResponse}

import scala.concurrent.duration._

object Drone {

  def apply(droneId: Long, operator: String, tick: FiniteDuration = 1.millis): Behavior[Command] =
    Behaviors.withTimers { timers =>
      docked(timers, droneId, operator, Position(0, 0), tick)
    }

  sealed trait Command
  final case object BootDrone                                                                           extends Command
  final case object TurnOffDrone                                                                        extends Command
  final case class Fly(flightRequest: FlightRequest, sender: ActorRef[FlightResponse])                  extends Command
  private final case class DuringFlight(flightRequest: FlightRequest, sender: ActorRef[FlightResponse]) extends Command
  final case class SetPosition(requestId: Long, position: Position, replyTo: ActorRef[PositionSet])     extends Command
  final case class ReadState(requestId: Long, replyTo: ActorRef[RespondState])                          extends Command
  final case class RespondState(requestId: Long, droneId: Long, position: Option[Position])
  final case class PositionSet(requestId: Long)

  def docked(timers: TimerScheduler[Command], id: Long, operator: String, position: Position, tick: FiniteDuration): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match {
        case BootDrone =>
          context.log.info(s"Drone [$operator | $id] booted")
          Behaviors.same
        case Fly(flyRequest: FlightRequest, replyTo: ActorRef[FlightResponse]) =>
          context.log.info(s"Drone [$operator | $id] accepted $flyRequest")
          context.self ! DuringFlight(flyRequest, replyTo)
          flying(timers, id, operator, position, tick)
        case TurnOffDrone =>
          context.log.info(s"Drone [$operator | $id] has been turned off")
          Behaviors.stopped
        case ReadState(requestId, replyTo) =>
          context.log.info(s"State for drone $id is $position")
          replyTo ! RespondState(requestId, id, Some(position))
          Behaviors.same
      }
    }

  def flying(timers: TimerScheduler[Command], id: Long, operator: String, position: Position, tick: FiniteDuration): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match {
        case DuringFlight(flightRequest: FlightRequest, replyTo: ActorRef[FlightResponse]) =>
          flightRequest.route match {
            case ::(head, tail) =>
              context.log.info(s"Drone [$operator | $id] is during flight at position $head")
              timers.startSingleTimer(DuringFlight(FlightRequest(flightRequest.id, tail), replyTo), tick)
              flying(timers, id, operator, head, tick)
            case Nil =>
              context.log.info(s"Drone [$operator | $id] has ended flight id ${flightRequest.id}")
              replyTo ! FlightResponse(flightRequest.id)
              docked(timers, id, operator, position, tick)
          }
        case TurnOffDrone =>
          context.log.info(s"Drone [$operator | $id] has been turned off")
          Behaviors.stopped
        case ReadState(requestId, replyTo) =>
          context.log.info(s"State for drone $id is $position")
          replyTo ! RespondState(requestId, id, Some(position))
          flying(timers, id, operator, position, tick)
      }
    }
}
