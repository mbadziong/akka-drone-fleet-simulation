package pl.mbadziong

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}

object DroneOperator {

  def apply(operatorName: String): Behavior[Command] =
    Behaviors.setup(context => new DroneOperator(context, operatorName))

  sealed trait Command
  final case class PrepareDroneFleet(dronesCount: Int) extends Command
  final case class StopDroneFleet()                    extends Command
}

class DroneOperator(context: ActorContext[DroneOperator.Command], operatorName: String)
    extends AbstractBehavior[DroneOperator.Command](context) {
  import DroneOperator._
  import pl.mbadziong.Drone.BootDrone

  val name: String           = operatorName
  private var droneIdToActor = Map.empty[Int, ActorRef[Drone.Command]]

  context.log.info(s"drone operator $name created")

  override def onMessage(msg: DroneOperator.Command): Behavior[DroneOperator.Command] = msg match {
    case PrepareDroneFleet(dronesCount) =>
      context.log.info(s"Initializing $dronesCount drones for operator $operatorName")
      (1 to dronesCount) map (
          droneNum => {
            val drone = context.spawn(Drone(droneNum, operatorName), s"drone-$droneNum")
            droneIdToActor += droneNum -> drone
            drone
          }
      ) foreach (
        _ ! BootDrone()
      )
      this
    case StopDroneFleet() =>
      Behaviors.stopped
  }

  override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
    case PostStop =>
      context.log.info(s"drone operator $operatorName stopped")
      this
  }
}
