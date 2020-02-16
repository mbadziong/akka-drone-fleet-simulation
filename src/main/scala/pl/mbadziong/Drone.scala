package pl.mbadziong

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors

object Drone {

  def apply(droneId: Int): Behavior[Command] =
    Behaviors.setup(context => new Drone(context, droneId))

  sealed trait Command
  final case class BootDrone() extends Command
  final case class DroneBooted(droneId: Int)
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
}
