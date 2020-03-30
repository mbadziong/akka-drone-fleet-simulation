package pl.mbadziong

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike
import pl.mbadziong.Drone.Fly
import pl.mbadziong.DroneOperator._
import pl.mbadziong.SimulationSupervisor._
import pl.mbadziong.airport.Airport
import pl.mbadziong.drone.Position
import pl.mbadziong.flight.{FlightCompleted, FlightRequest, FlightResponse}

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
      drone1Actor ! Fly(FlightRequest(1, List(Position(1, 1))), flightProbe.ref)
      flightProbe.expectMessage(FlightCompleted(1))
      drone2Actor ! Fly(FlightRequest(2, List(Position(2, 2))), flightProbe.ref)
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

    "be able to handle flight request by one of owned drones" in {
      val droneAddedProbe = createTestProbe[DroneAddedToFleet]()
      val operatorActor   = spawn(DroneOperator("Mateusz", Airport(Position(0.0, 0.0))))
      val flightId        = 5L
      val longRoute = List.from(1 to 100 map { i =>
        Position(i, i)
      })

      operatorActor ! AddDroneToFleet(1, droneAddedProbe.ref)
      // this drone will be busy doing flight with a lot of points
      val busyDrone = droneAddedProbe.receiveMessage().drone
      // this drone will be free and should handle flight request
      operatorActor ! AddDroneToFleet(2, droneAddedProbe.ref)
      val ignoredFlightProbe = createTestProbe[FlightResponse]()
      busyDrone ! Fly(FlightRequest(flightId, longRoute), ignoredFlightProbe.ref)
      val flightStatusProbe = createTestProbe[HandleFlightResponse]()

      operatorActor ! HandleFly(FlightRequest(flightId, List(Position(1, 1))), flightStatusProbe.ref)

      flightStatusProbe.expectMessage(HandleFlightResponse(FlightCompleted(flightId)))
    }
  }
}
