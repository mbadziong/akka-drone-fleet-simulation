package pl.mbadziong

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{Behavior, PostStop, Signal}

object Drone {

  def apply(droneId: Int): Behavior[Command] =
    Behaviors.setup(context => new Drone(context, droneId))

  sealed trait Command
  final case class BootDrone()    extends Command
  final case class TurnOffDrone() extends Command
}

class Drone(context: ActorContext[Drone.Command], droneId: Int) extends AbstractBehavior[Drone.Command](context) {
  import Drone._

  var id: Int = droneId

  context.log.info(s"Drone $id has been created")

  override def onMessage(msg: Command): Behavior[Command] = {
    msg match {
      case BootDrone() =>
        context.log.info(s"Drone $id booted")
        this
      case TurnOffDrone() =>
        context.log.info(s"Drone $id has been turned off")
        this
    }
  }

  override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
    case PostStop =>
      println(s"drone $id has been stopped")
      this
  }
}
