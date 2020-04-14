package pl.mbadziong.drone

import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import pl.mbadziong.airport.Airport
import pl.mbadziong.common.Position
import pl.mbadziong.flight.{Flight, FlightCompleted, FlightResponse}

import scala.concurrent.duration._

object Drone {

  private val DRONE_TICK = "simulator.drone.tick"

  def apply(droneId: Long, operator: String, airport: Airport): Behavior[Command] =
    Behaviors.setup { context =>
      Behaviors.withTimers { timers =>
        val tick = FiniteDuration(context.system.settings.config.getDuration(DRONE_TICK).toMillis, MILLISECONDS)
        docked(timers, droneId, operator, airport.position, tick)
      }
    }

  sealed trait Command
  final case object BootDrone                                                                       extends Command
  final case object TurnOffDrone                                                                    extends Command
  final case class Fly(flight: Flight, sender: ActorRef[FlightResponse])                            extends Command
  private final case class DuringFlight(flight: Flight, sender: ActorRef[FlightResponse])           extends Command
  final case class SetPosition(requestId: Long, position: Position, replyTo: ActorRef[PositionSet]) extends Command
  final case class ReadState(requestId: Long, replyTo: ActorRef[RespondState])                      extends Command
  final case class RespondState(requestId: Long, droneId: Long, position: Option[Position])
  final case class PositionSet(requestId: Long)

  def docked(timers: TimerScheduler[Command], id: Long, operator: String, position: Position, tick: FiniteDuration): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match {
        case BootDrone =>
          context.log.info(s"Drone [$operator | $id] booted")
          Behaviors.same
        case Fly(flight: Flight, replyTo: ActorRef[FlightResponse]) =>
          context.log.info(s"Drone [$operator | $id] accepted $flight")
          context.self ! DuringFlight(flight, replyTo)
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
        case DuringFlight(flight: Flight, replyTo: ActorRef[FlightResponse]) =>
          flight.route match {
            case ::(head, tail) =>
              context.log.info(s"Drone [$operator | $id] is during flight at position $head")
              timers.startSingleTimer(DuringFlight(Flight(flight.id, tail), replyTo), tick)
              flying(timers, id, operator, head, tick)
            case Nil =>
              context.log.info(s"Drone [$operator | $id] has ended flight id ${flight.id}")
              replyTo ! FlightCompleted(flight.id)
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
