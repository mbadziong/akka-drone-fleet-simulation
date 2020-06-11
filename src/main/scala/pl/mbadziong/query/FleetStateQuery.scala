package pl.mbadziong.query

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import pl.mbadziong.airport.Airport
import pl.mbadziong.drone.Drone
import pl.mbadziong.drone.Drone.RespondState
import pl.mbadziong.query.FleetStateQuery.{CollectionTimeout, Command, DroneTerminated, WrappedRespondState}
import pl.mbadziong.supervisor.SimulationSupervisor
import pl.mbadziong.supervisor.SimulationSupervisor._

import scala.concurrent.duration.FiniteDuration

object FleetStateQuery {

  def apply(
      droneIdToActor: Map[Long, ActorRef[Drone.Command]],
      airport: Airport,
      requestId: Long,
      requester: ActorRef[SimulationSupervisor.RespondFleetState],
      timeout: FiniteDuration
  ): Behavior[Command] = {
    Behaviors.setup { context =>
      Behaviors.withTimers { timers =>
        new FleetStateQuery(droneIdToActor, airport, requestId, requester, timeout, context, timers)
      }
    }
  }

  trait Command

  private case object CollectionTimeout                        extends Command
  final case class WrappedRespondState(response: RespondState) extends Command
  private final case class DroneTerminated(droneId: Long)      extends Command
}

class FleetStateQuery(droneIdToActor: Map[Long, ActorRef[Drone.Command]],
                      airport: Airport,
                      requestId: Long,
                      requester: ActorRef[SimulationSupervisor.RespondFleetState],
                      timeout: FiniteDuration,
                      context: ActorContext[FleetStateQuery.Command],
                      timers: TimerScheduler[FleetStateQuery.Command])
    extends AbstractBehavior[FleetStateQuery.Command](context) {

  context.log.info(s"FleetStateQuery for request id $requestId started")

  timers.startSingleTimer(CollectionTimeout, CollectionTimeout, timeout)

  private val respondDroneStateAdapter = context.messageAdapter(WrappedRespondState.apply)

  private var repliesSoFar = Map.empty[Long, DroneState]
  private var stillWaiting = droneIdToActor.keySet

  context.log.info(s"Collecting state for drones: $droneIdToActor")

  droneIdToActor.foreach {
    case (droneId, drone) =>
      context.log.info(s"Sending ReadState request for $droneId")
      context.watchWith(drone, DroneTerminated(droneId))
      drone ! Drone.ReadState(0, respondDroneStateAdapter)
  }

  override def onMessage(msg: Command): Behavior[Command] = {
    context.log.info(s"onMessage with message $msg")
    msg match {
      case WrappedRespondState(response) => onRespondState(response)
      case DroneTerminated(droneId)      => onDroneTerminated(droneId)
      case CollectionTimeout             => onCollectionTimeout()
    }
  }

  private def onRespondState(response: Drone.RespondState): Behavior[Command] = {

    context.log.info(s"onRespondState: $response")
    val droneState: DroneState = response.position match {
      case Some(dronePosition) if dronePosition.lon == airport.position.lon && dronePosition.lat == airport.position.lat => ReadyToFlight
      case Some(pos)                                                                                                     => InFlight(pos)
      case None                                                                                                          => LoadsBattery
    }

    val droneId = response.droneId
    repliesSoFar += (droneId -> droneState)
    stillWaiting -= droneId

    respondWhenAllCollected()
  }

  private def onDroneTerminated(droneId: Long): Behavior[Command] = {
    context.log.info(s"onDroneTerminated: $droneId")
    if (stillWaiting(droneId)) {
      repliesSoFar += (droneId -> TimedOut)
      stillWaiting -= droneId
    }
    respondWhenAllCollected()
  }

  private def onCollectionTimeout(): Behavior[Command] = {
    context.log.info(s"onCollectionTimeout")
    repliesSoFar ++= stillWaiting.map(droneId => droneId -> TimedOut)
    stillWaiting = Set.empty
    respondWhenAllCollected()
  }

  private def respondWhenAllCollected(): Behavior[Command] = {
    context.log.info(s"respondWhenAllCollected")
    if (stillWaiting.isEmpty) {
      context.log.info(s"Responding fleet state: $repliesSoFar")
      requester ! RespondFleetState(requestId, repliesSoFar)
      Behaviors.stopped
    } else {
      this
    }
  }
}
