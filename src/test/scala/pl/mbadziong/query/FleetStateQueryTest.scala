package pl.mbadziong.query

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike
import pl.mbadziong.airport.Airport
import pl.mbadziong.common.Position
import pl.mbadziong.drone.Drone
import pl.mbadziong.query.FleetStateQuery.WrappedRespondState
import pl.mbadziong.supervisor.SimulationSupervisor._

import scala.concurrent.duration._

class FleetStateQueryTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "Fleet State Query actor" must {

    "return state for operator's drones" in {
      val requester  = createTestProbe[RespondFleetState]()
      val airportLat = 0.0
      val airportLon = 0.0

      val drone1 = createTestProbe[Drone.Command]()
      val drone2 = createTestProbe[Drone.Command]()

      val droneIdToActor = Map(1L -> drone1.ref, 2L -> drone2.ref)

      val queryActor =
        spawn(
          FleetStateQuery(droneIdToActor,
                          Airport(Position(airportLat, airportLon)),
                          requestId = 1,
                          requester = requester.ref,
                          timeout = 3.seconds))

      drone1.expectMessageType[Drone.ReadState]
      drone2.expectMessageType[Drone.ReadState]

      queryActor ! WrappedRespondState(Drone.RespondState(requestId = 0, 1L, Some(Position(1, 1))))
      queryActor ! WrappedRespondState(Drone.RespondState(requestId = 0, 2L, Some(Position(2, 2))))

      requester.expectMessage(RespondFleetState(requestId = 1, state = Map(1L -> InFlight(Position(1, 1)), 2L -> InFlight(Position(2, 2)))))
    }

    "return ReadyToFlight state for drones staying in airport" in {
      val requester  = createTestProbe[RespondFleetState]()
      val airportLat = 0.0
      val airportLon = 0.0

      val drone1 = createTestProbe[Drone.Command]()
      val drone2 = createTestProbe[Drone.Command]()
      val drone3 = createTestProbe[Drone.Command]()

      val droneIdToActor = Map(1L -> drone1.ref, 2L -> drone2.ref, 3L -> drone3.ref)

      val queryActor =
        spawn(
          FleetStateQuery(droneIdToActor,
                          Airport(Position(airportLat, airportLon)),
                          requestId = 1,
                          requester = requester.ref,
                          timeout = 3.seconds))

      drone1.expectMessageType[Drone.ReadState]
      drone2.expectMessageType[Drone.ReadState]
      drone3.expectMessageType[Drone.ReadState]

      queryActor ! WrappedRespondState(Drone.RespondState(requestId = 0, 1L, Some(Position(0, 0))))
      queryActor ! WrappedRespondState(Drone.RespondState(requestId = 0, 2L, Some(Position(0, 0))))
      queryActor ! WrappedRespondState(Drone.RespondState(requestId = 0, 3L, Some(Position(1.0, 1.0))))

      requester.expectMessage(
        RespondFleetState(requestId = 1, state = Map(1L -> ReadyToFlight, 2L -> ReadyToFlight, 3L -> InFlight(Position(1.0, 1.0)))))
    }

    "return LoadsBattery state for drones with turned off GPS" in {
      val requester  = createTestProbe[RespondFleetState]()
      val airportLat = 0.0
      val airportLon = 0.0

      val drone1 = createTestProbe[Drone.Command]()
      val drone2 = createTestProbe[Drone.Command]()
      val drone3 = createTestProbe[Drone.Command]()

      val droneIdToActor = Map(1L -> drone1.ref, 2L -> drone2.ref, 3L -> drone3.ref)

      val queryActor =
        spawn(
          FleetStateQuery(droneIdToActor,
                          Airport(Position(airportLat, airportLon)),
                          requestId = 1,
                          requester = requester.ref,
                          timeout = 3.seconds))

      drone1.expectMessageType[Drone.ReadState]
      drone2.expectMessageType[Drone.ReadState]
      drone3.expectMessageType[Drone.ReadState]

      queryActor ! WrappedRespondState(Drone.RespondState(requestId = 0, 1L, None))
      queryActor ! WrappedRespondState(Drone.RespondState(requestId = 0, 2L, Some(Position(0, 0))))
      queryActor ! WrappedRespondState(Drone.RespondState(requestId = 0, 3L, Some(Position(1.0, 1.0))))

      requester.expectMessage(
        RespondFleetState(requestId = 1, state = Map(1L -> LoadsBattery, 2L -> ReadyToFlight, 3L -> InFlight(Position(1.0, 1.0)))))
    }

    "return TimedOut state if drone doesnt answer in time" in {
      val requester  = createTestProbe[RespondFleetState]()
      val airportLat = 0.0
      val airportLon = 0.0

      val drone1 = createTestProbe[Drone.Command]()
      val drone2 = createTestProbe[Drone.Command]()
      val drone3 = createTestProbe[Drone.Command]()

      val droneIdToActor = Map(1L -> drone1.ref, 2L -> drone2.ref, 3L -> drone3.ref)

      val queryActor =
        spawn(
          FleetStateQuery(droneIdToActor,
                          Airport(Position(airportLat, airportLon)),
                          requestId = 1,
                          requester = requester.ref,
                          timeout = 1.seconds))

      drone1.expectMessageType[Drone.ReadState]
      drone2.expectMessageType[Drone.ReadState]
      drone3.expectMessageType[Drone.ReadState]

      queryActor ! WrappedRespondState(Drone.RespondState(requestId = 0, 1L, None))
      queryActor ! WrappedRespondState(Drone.RespondState(requestId = 0, 2L, Some(Position(0, 0))))

      requester.expectMessage(RespondFleetState(requestId = 1, state = Map(1L -> LoadsBattery, 2L -> ReadyToFlight, 3L -> TimedOut)))
    }

  }
}
