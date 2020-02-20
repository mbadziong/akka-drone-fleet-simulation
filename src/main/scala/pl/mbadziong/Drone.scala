package pl.mbadziong

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import pl.mbadziong.flight.{FlightRequest, FlightResponse}

object Drone {

  def apply(droneId: Int, operator: String): Behavior[Command] =
    Behaviors.setup(context => new Drone(context, droneId, operator))

  sealed trait Command
  final case class BootDrone()                                                         extends Command
  final case class TurnOffDrone()                                                      extends Command
  final case class Fly(flightRequest: FlightRequest, sender: ActorRef[FlightResponse]) extends Command
}

class Drone(context: ActorContext[Drone.Command], val id: Int, val operator: String) extends AbstractBehavior[Drone.Command](context) {
  import Drone._

  context.log.info(s"Drone [$operator | $id] has been created")

  override def onMessage(msg: Command): Behavior[Command] = {
    msg match {
      case BootDrone() =>
        context.log.info(s"Drone [$operator | $id] booted")
        this
      case TurnOffDrone() =>
        context.log.info(s"Drone [$operator | $id] has been turned off")
        this
      case Fly(flyRequest: FlightRequest, sender: ActorRef[FlightResponse]) =>
        context.log.info(s"Drone [$operator | $id] is during $flyRequest")
        sender ! new FlightResponse(flyRequest.id)
        this
    }
  }

  override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
    case PostStop =>
      println(s"drone [$operator | $id] has been stopped")
      this
  }
}
