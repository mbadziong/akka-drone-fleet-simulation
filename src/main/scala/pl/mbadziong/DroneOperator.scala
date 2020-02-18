package pl.mbadziong

import akka.actor.typed.{Behavior, PostStop, Signal}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}

object DroneOperator {

  def apply(): Behavior[Command] =
    Behaviors.setup(context => new DroneOperator(context))

  sealed trait Command
  final case class PrepareDroneFleet(dronesCount: Int) extends Command
  final case class StopDroneFleet()                    extends Command
}

class DroneOperator(context: ActorContext[DroneOperator.Command]) extends AbstractBehavior[DroneOperator.Command](context) {
  import DroneOperator._
  import pl.mbadziong.Drone.BootDrone

  override def onMessage(msg: DroneOperator.Command): Behavior[DroneOperator.Command] = msg match {
    case PrepareDroneFleet(dronesCount) =>
      context.log.info(s"Initializing $dronesCount drones")
      (1 to dronesCount) map (
          droneNum => context.spawn(Drone(droneNum), s"drone-$droneNum")
      ) foreach (_ ! BootDrone())
      this
    case StopDroneFleet() =>
      Behaviors.stopped
  }

  override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
    case PostStop =>
      context.log.info("drone operator stopped")
      this
  }
}
