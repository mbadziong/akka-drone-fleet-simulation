package pl.mbadziong.operator

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike
import pl.mbadziong.airport.{ARKONSKA_GDANSK_AIRPORT, Airport}
import pl.mbadziong.common.{NEAR_GDANSK_ARKONSKA_AIRPORT, Position}
import pl.mbadziong.drone.Drone
import pl.mbadziong.drone.Drone.Fly
import pl.mbadziong.flight._
import pl.mbadziong.operator.DroneOperator.{apply => _, _}
import pl.mbadziong.supervisor.SimulationSupervisor._

class DroneOperatorTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "Drone operator actor" must {

    val droneOperator = "Mateusz"

    "be able to list all owned drones" in {
      val droneCount               = 5
      val droneOperatorActor       = spawn(DroneOperator(droneOperator, Airport(Position(0.0, 0.0))))
      val createdDronesProbe       = createTestProbe[ReplyFleet]()
      val createDroneOperatorProbe = createTestProbe[DroneFleetCreated]()

      droneOperatorActor ! PrepareDroneFleet(droneCount, createDroneOperatorProbe.ref)
      createDroneOperatorProbe.expectMessageType[DroneFleetCreated]

      droneOperatorActor ! RequestFleet(1L, droneOperator, createdDronesProbe.ref)
      createdDronesProbe.expectMessage(ReplyFleet(1L, Set(0, 1, 2, 3, 4)))
    }

    "be able to ignore requests for wrong operator" in {
      val droneCount               = 5
      val droneOperatorActor       = spawn(DroneOperator(droneOperator, Airport(Position(0.0, 0.0))))
      val createdDronesProbe       = createTestProbe[ReplyFleet]()
      val createDroneOperatorProbe = createTestProbe[DroneFleetCreated]()

      droneOperatorActor ! PrepareDroneFleet(droneCount, createDroneOperatorProbe.ref)
      createDroneOperatorProbe.expectMessageType[DroneFleetCreated]

      droneOperatorActor ! RequestFleet(1L, "Test", createdDronesProbe.ref)
      createdDronesProbe.expectNoMessage()
    }

    "be able to list active drones after one shuts down" in {
      val droneCount         = 5
      val droneOperatorActor = spawn(DroneOperator(droneOperator, Airport(Position(0.0, 0.0))))
      val probe              = createTestProbe[DroneFleetCreated]()

      droneOperatorActor ! PrepareDroneFleet(droneCount, probe.ref)
      val response        = probe.receiveMessage()
      val firstActorDrone = response.droneActors(0)
      firstActorDrone ! Drone.TurnOffDrone
      probe.expectTerminated(firstActorDrone, probe.remainingOrDefault)

      val ownedDronesProbe = createTestProbe[ReplyFleet]()
      droneOperatorActor ! RequestFleet(1L, droneOperator, ownedDronesProbe.ref)

      ownedDronesProbe.expectMessage(ReplyFleet(1L, Set(1, 2, 3, 4)))
    }

    "be able to collect state of all owned drones" in {
      val droneAddedProbe = createTestProbe[DroneAddedToFleet]()
      val operatorActor   = spawn(DroneOperator(droneOperator, Airport(Position(0.0, 0.0))))

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
      operatorActor ! RequestFleetState(requestId = 3, operator = droneOperator, allStateProbe.ref)
      allStateProbe.expectMessage(
        RespondFleetState(
          requestId = 3,
          state = Map(1L -> InFlight(Position(1.0, 1.0)), 2L -> InFlight(Position(2.0, 2.0)))
        )
      )
    }

    "be able to handle flight by one of owned drones" in {
      val droneAddedProbe = createTestProbe[DroneAddedToFleet]()
      val operatorActor   = spawn(DroneOperator(droneOperator, ARKONSKA_GDANSK_AIRPORT))
      val flightId        = 5L

      operatorActor ! AddDroneToFleet(1, droneAddedProbe.ref)
      val fleetStateProbe     = createTestProbe[RespondFleetState]()
      val flightResponseProbe = createTestProbe[HandleFlightResponse]()

      operatorActor ! HandleFly(FlightRequest(flightId, NEAR_GDANSK_ARKONSKA_AIRPORT), flightResponseProbe.ref)
      Thread.sleep(200)
      operatorActor ! RequestFleetState(1L, droneOperator, fleetStateProbe.ref)

      val msg = fleetStateProbe.receiveMessage()
      msg.requestId should be(1)
      msg.state.keys.size should be(1)
      msg.state(1) shouldBe a[InFlight]
      flightResponseProbe.expectMessage(HandleFlightResponse(FlightAccepted(flightId)))
    }

    "deny flight when all drones are busy" in {
      val droneAddedProbe  = createTestProbe[DroneAddedToFleet]()
      val operatorActor    = spawn(DroneOperator(droneOperator, ARKONSKA_GDANSK_AIRPORT))
      val acceptedFlightId = 5L
      val deniedFlightId   = 6L

      operatorActor ! AddDroneToFleet(1, droneAddedProbe.ref)
      val acceptedFlightResponseProbe = createTestProbe[HandleFlightResponse]()
      val deniedFlightResponseProbe   = createTestProbe[HandleFlightResponse]()

      operatorActor ! HandleFly(FlightRequest(acceptedFlightId, NEAR_GDANSK_ARKONSKA_AIRPORT), acceptedFlightResponseProbe.ref)
      Thread.sleep(200)
      operatorActor ! HandleFly(FlightRequest(deniedFlightId, NEAR_GDANSK_ARKONSKA_AIRPORT), deniedFlightResponseProbe.ref)
      Thread.sleep(100)

      acceptedFlightResponseProbe.expectMessage(HandleFlightResponse(FlightAccepted(acceptedFlightId)))
      deniedFlightResponseProbe.expectMessage(
        HandleFlightResponse(
          FlightDenied(deniedFlightId, s"Operator $droneOperator does not have any drone able to handle fly request $deniedFlightId")))
    }
  }
}
