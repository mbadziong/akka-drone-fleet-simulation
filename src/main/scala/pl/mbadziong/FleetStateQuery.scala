package pl.mbadziong

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import pl.mbadziong.DroneSimulationSupervisor._
import pl.mbadziong.FleetStateQuery.{CollectionTimeout, Command, DroneTerminated, WrappedRespondState}

import scala.concurrent.duration.FiniteDuration

object FleetStateQuery {

  def apply(
      droneIdToActor: Map[Long, ActorRef[Drone.Command]],
      requestId: Long,
      requester: ActorRef[DroneSimulationSupervisor.RespondFleetState],
      timeout: FiniteDuration
  ): Behavior[Command] = {
    Behaviors.setup { context =>
      Behaviors.withTimers { timers =>
        new FleetStateQuery(droneIdToActor, requestId, requester, timeout, context, timers)
      }
    }
  }

  trait Command

  private case object CollectionTimeout                              extends Command
  final case class WrappedRespondState(response: Drone.RespondState) extends Command
  private final case class DroneTerminated(droneId: Long)            extends Command
}

class FleetStateQuery(droneIdToActor: Map[Long, ActorRef[Drone.Command]],
                      requestId: Long,
                      requester: ActorRef[DroneSimulationSupervisor.RespondFleetState],
                      timeout: FiniteDuration,
                      context: ActorContext[FleetStateQuery.Command],
                      timers: TimerScheduler[FleetStateQuery.Command])
    extends AbstractBehavior[FleetStateQuery.Command](context) {

  timers.startSingleTimer(CollectionTimeout, CollectionTimeout, timeout)

  private val respondDroneStateAdapter = context.messageAdapter(WrappedRespondState.apply)

  private var repliesSoFar = Map.empty[Long, DroneState]
  private var stillWaiting = droneIdToActor.keySet

  droneIdToActor.foreach {
    case (droneId, drone: Drone) =>
      context.watchWith(drone, DroneTerminated(droneId))
      drone ! Drone.ReadState(0, respondDroneStateAdapter)
  }

  override def onMessage(msg: Command): Behavior[Command] =
    msg match {
      case WrappedRespondState(response) => onRespondState(response)
      case DroneTerminated(droneId)      => onDroneTerminated(droneId)
      case CollectionTimeout             => onCollectionTimeout()
    }

  private def onRespondState(response: Drone.RespondState): Behavior[Command] = {
    val droneState = response.position match {
      case Some(value) if value.lon > 0 && value.lat > 0 => InFlight(value)
      case Some(_)                                       => ReadyToFlight
      case None                                          => LoadsBattery
    }

    val droneId = response.droneId
    repliesSoFar += (droneId -> droneState)
    stillWaiting -= droneId

    respondWhenAllCollected()
  }

  private def onDroneTerminated(droneId: Long): Behavior[Command] = {
    if (stillWaiting(droneId)) {
      repliesSoFar += (droneId -> TimedOut)
      stillWaiting -= droneId
    }
    respondWhenAllCollected()
  }

  private def onCollectionTimeout(): Behavior[Command] = {
    repliesSoFar ++= stillWaiting.map(droneId => droneId -> TimedOut)
    stillWaiting = Set.empty
    respondWhenAllCollected()
  }

  private def respondWhenAllCollected(): Behavior[Command] = {
    if (stillWaiting.isEmpty) {
      requester ! RespondFleetState(requestId, repliesSoFar)
      Behaviors.stopped
    } else {
      this
    }
  }
}
