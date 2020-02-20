package pl.mbadziong

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike
import pl.mbadziong.flight.{FlightRequest, FlightResponse}

class DroneTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "Drone actor" must {

    "reply with response for given flight request" in {
      val flightId      = 1
      val probe         = createTestProbe[FlightResponse]()
      val droneActor    = spawn(Drone(flightId, "test"))
      val flightRequest = new FlightRequest(1)

      droneActor ! Drone.Fly(flightRequest, probe.ref)
      val response = probe.receiveMessage()

      response.id should ===(flightId)
    }
  }
}
