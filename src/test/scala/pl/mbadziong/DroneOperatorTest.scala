package pl.mbadziong

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike
import pl.mbadziong.Drone.Fly
import pl.mbadziong.DroneOperator._
import pl.mbadziong.SimulationSupervisor._
import pl.mbadziong.airport.{ARKONSKA_GDANSK_AIRPORT, Airport}
import pl.mbadziong.drone.{NEAR_GDANSK_ARKONSKA_AIRPORT, Position}
import pl.mbadziong.flight.{Flight, FlightCompleted, FlightRequest, FlightResponse}

class DroneOperatorTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "Drone operator actor" must {

    "be able to list all owned drones" in {
      val droneCount               = 5
      val droneOperatorActor       = spawn(DroneOperator("Mateusz", Airport(Position(0.0, 0.0))))
      val createdDronesProbe       = createTestProbe[ReplyFleet]()
      val createDroneOperatorProbe = createTestProbe[DroneFleetCreated]()

      droneOperatorActor ! PrepareDroneFleet(droneCount, createDroneOperatorProbe.ref)
      createDroneOperatorProbe.expectMessageType[DroneFleetCreated]

      droneOperatorActor ! RequestFleet(1L, "Mateusz", createdDronesProbe.ref)
      createdDronesProbe.expectMessage(ReplyFleet(1L, Set(0, 1, 2, 3, 4)))
    }

    "be able to ignore requests for wrong operator" in {
      val droneCount               = 5
      val droneOperatorActor       = spawn(DroneOperator("Mateusz", Airport(Position(0.0, 0.0))))
      val createdDronesProbe       = createTestProbe[ReplyFleet]()
      val createDroneOperatorProbe = createTestProbe[DroneFleetCreated]()

      droneOperatorActor ! PrepareDroneFleet(droneCount, createDroneOperatorProbe.ref)
      createDroneOperatorProbe.expectMessageType[DroneFleetCreated]

      droneOperatorActor ! RequestFleet(1L, "Test", createdDronesProbe.ref)
      createdDronesProbe.expectNoMessage()
    }

    "be able to list active drones after one shuts down" in {
      val droneCount         = 5
      val droneOperatorActor = spawn(DroneOperator("Mateusz", Airport(Position(0.0, 0.0))))
      val probe              = createTestProbe[DroneFleetCreated]()

      droneOperatorActor ! PrepareDroneFleet(droneCount, probe.ref)
      val response        = probe.receiveMessage()
      val firstActorDrone = response.droneActors(0)
      firstActorDrone ! Drone.TurnOffDrone
      probe.expectTerminated(firstActorDrone, probe.remainingOrDefault)

      val ownedDronesProbe = createTestProbe[ReplyFleet]()
      droneOperatorActor ! RequestFleet(1L, "Mateusz", ownedDronesProbe.ref)

      ownedDronesProbe.expectMessage(ReplyFleet(1L, Set(1, 2, 3, 4)))
    }

    "be able to collect state of all owned drones" in {
      val droneAddedProbe = createTestProbe[DroneAddedToFleet]()
      val operatorActor   = spawn(DroneOperator("Mateusz", Airport(Position(0.0, 0.0))))

      operatorActor ! AddDroneToFleet(1, droneAddedProbe.ref)
      val drone1Actor = droneAddedProbe.receiveMessage().drone

      operatorActor ! AddDroneToFleet(2, droneAddedProbe.ref)
      val drone2Actor = droneAddedProbe.receiveMessage().drone

      val flightProbe = createTestProbe[FlightResponse]()
      drone1Actor ! Fly(Flight(1, List(Position(1, 1))), flightProbe.ref)
      flightProbe.expectMessage(FlightCompleted(1))
      drone2Actor ! Fly(Flight(2, List(Position(2, 2))), flightProbe.ref)
      flightProbe.expectMessage(FlightCompleted(2))

      val allStateProbe = createTestProbe[RespondFleetState]()
      operatorActor ! RequestFleetState(requestId = 3, operator = "Mateusz", allStateProbe.ref)
      allStateProbe.expectMessage(
        RespondFleetState(
          requestId = 3,
          state = Map(1L -> InFlight(Position(1.0, 1.0)), 2L -> InFlight(Position(2.0, 2.0)))
        )
      )
    }

    "be able to handle flight by one of owned drones" in {
      val droneAddedProbe = createTestProbe[DroneAddedToFleet]()
      val operatorActor   = spawn(DroneOperator("Mateusz", ARKONSKA_GDANSK_AIRPORT))
      val flightId        = 5L

      operatorActor ! AddDroneToFleet(1, droneAddedProbe.ref)
      val fleetStateProbe = createTestProbe[RespondFleetState]()

      operatorActor ! HandleFly(FlightRequest(flightId, NEAR_GDANSK_ARKONSKA_AIRPORT))
      Thread.sleep(1000)
      operatorActor ! RequestFleetState(1L, "Mateusz", fleetStateProbe.ref)

      val msg = fleetStateProbe.receiveMessage()
      msg.requestId should be(1)
      msg.state.keys.size should be(1)
      msg.state(1) shouldBe a[InFlight]
    }
  }
}
