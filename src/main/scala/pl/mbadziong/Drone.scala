package pl.mbadziong

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import pl.mbadziong.Drone.Command
import pl.mbadziong.drone.Position
import pl.mbadziong.flight.{FlightRequest, FlightResponse}

object Drone {

  def apply(droneId: Long, operator: String): Behavior[Command] =
    Behaviors.setup { context =>
      Behaviors.withTimers { timers =>
        new Drone(context, timers, droneId, operator)
      }
    }

  sealed trait Command
  final case object BootDrone                                                                       extends Command
  final case object TurnOffDrone                                                                    extends Command
  final case class Fly(flightRequest: FlightRequest, sender: ActorRef[FlightResponse])              extends Command
  final case class DuringFlight(flightRequest: FlightRequest, sender: ActorRef[FlightResponse])     extends Command
  final case class SetPosition(requestId: Long, position: Position, replyTo: ActorRef[PositionSet]) extends Command
  final case class ReadState(requestId: Long, replyTo: ActorRef[RespondState])                      extends Command
  final case class RespondState(requestId: Long, droneId: Long, position: Option[Position])
  final case class PositionSet(requestId: Long)
}

class Drone(context: ActorContext[Drone.Command], timers: TimerScheduler[Command], val id: Long, val operator: String)
    extends AbstractBehavior[Drone.Command](context) {
  import Drone._

  var position: Option[Position] = None

  context.log.info(s"Drone [$operator | $id] has been created")

  override def onMessage(msg: Command): Behavior[Command] = {
    msg match {
      case BootDrone =>
        position = Some(Position(0, 0))
        context.log.info(s"Drone [$operator | $id] booted")
        this
      case TurnOffDrone =>
        context.log.info(s"Drone [$operator | $id] has been turned off")
        Behaviors.stopped
      case ReadState(requestId, replyTo) =>
        context.log.info(s"State for drone $id is $position")
        replyTo ! RespondState(requestId, id, position)
        this
      case SetPosition(requestId, position, replyTo) =>
        this.position = Some(position)
        context.log.info(s"Drone [$operator | $id] set position to ${position.lat} and ${position.lon}, requestId: $requestId")
        replyTo ! PositionSet(requestId)
        this
      case Fly(flyRequest: FlightRequest, replyTo: ActorRef[FlightResponse]) =>
        context.log.info(s"Drone [$operator | $id] accepted $flyRequest")
        context.self ! DuringFlight(flyRequest, replyTo)
        this
      case DuringFlight(flightRequest: FlightRequest, replyTo: ActorRef[FlightResponse]) =>
        flightRequest.route match {
          case ::(head, tail) =>
            context.log.info(s"Drone [$operator | $id] is during flight at position $head")
            position = Some(head)
            context.self ! DuringFlight(new FlightRequest(flightRequest.id, tail), replyTo)
          case Nil =>
            context.log.info(s"Drone [$operator | $id] has ended flight id ${flightRequest.id}")
            replyTo ! new FlightResponse(flightRequest.id)
        }
        this
    }
  }

  override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
    case PostStop =>
      context.log.info(s"drone [$operator | $id] has been stopped")
      this
  }
}
