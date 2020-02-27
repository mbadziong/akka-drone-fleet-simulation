package pl.mbadziong

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike
import pl.mbadziong.Drone.RespondState
import pl.mbadziong.drone.Position
import pl.mbadziong.flight.{FlightRequest, FlightResponse}

class DroneTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "Drone actor" must {

    "reply with response for given flight request" in {
      val droneId       = 1
      val flightId      = 2
      val probe         = createTestProbe[FlightResponse]()
      val droneActor    = spawn(Drone(droneId, "test"))
      val flightRequest = new FlightRequest(flightId)

      droneActor ! Drone.Fly(flightRequest, probe.ref)
      val response = probe.receiveMessage()

      response.id should ===(flightId)
    }

    "have initial state of 0 lat and 0 lon after booting up" in {
      val droneId = 1
      val requestId = 2
      val probe = createTestProbe[RespondState]()
      val droneActor = spawn(Drone(droneId, "test"))

      droneActor ! Drone.BootDrone
      probe.expectNoMessage()
      droneActor ! Drone.ReadState(requestId, probe.ref)
      val response = probe.receiveMessage()

      response.position.contains(Position(0,0))
      response.droneId === droneId
      response.requestId === requestId
    }
  }
}
