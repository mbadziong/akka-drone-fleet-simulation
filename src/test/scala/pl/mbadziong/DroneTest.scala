package pl.mbadziong

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike
import pl.mbadziong.Drone.RespondState
import pl.mbadziong.drone.Position
import pl.mbadziong.flight.{FlightCompleted, FlightRequest, FlightResponse}

class DroneTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "Drone actor" must {

    "reply with response for given flight request" in {
      val droneId       = 1
      val flightId      = 2
      val probe         = createTestProbe[FlightResponse]()
      val droneActor    = spawn(Drone(droneId, "test"))
      val flightRequest = FlightRequest(flightId, List(Position(1.0, 1.0), Position(1.0, 2.0), Position(1.0, 3.0)))

      droneActor ! Drone.Fly(flightRequest, probe.ref)
      val response = probe.receiveMessage

      response should be(FlightCompleted(flightId))
    }

    "have initial state of 0 lat and 0 lon after booting up" in {
      val droneId    = 1
      val requestId  = 2
      val probe      = createTestProbe[RespondState]()
      val droneActor = spawn(Drone(droneId, "test"))

      droneActor ! Drone.BootDrone
      probe.expectNoMessage()
      droneActor ! Drone.ReadState(requestId, probe.ref)
      val response = probe.receiveMessage()

      response.position.contains(Position(0, 0))
      response.droneId === droneId
      response.requestId === requestId
    }

    "stay in the last position of given route after flight" in {
      val droneId       = 1
      val flightId      = 2
      val probe         = createTestProbe[FlightResponse]()
      val droneActor    = spawn(Drone(droneId, "test"))
      val flightRequest = FlightRequest(flightId, List(Position(1.0, 1.0), Position(1.0, 2.0), Position(1.0, 3.0)))

      droneActor ! Drone.Fly(flightRequest, probe.ref)
      probe.receiveMessage

      val positionProbe = createTestProbe[RespondState]()
      droneActor ! Drone.ReadState(3, positionProbe.ref)
      val positionResponse = positionProbe.receiveMessage

      positionResponse.position === Some(1.0, 3.0)
    }
  }
}
