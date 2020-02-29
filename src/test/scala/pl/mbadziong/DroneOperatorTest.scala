package pl.mbadziong

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike
import pl.mbadziong.Drone.{PositionSet, SetPosition}
import pl.mbadziong.DroneOperator._
import pl.mbadziong.SimulationSupervisor.{DroneFleetCreated, InFlight, RequestFleetState, RespondFleetState}
import pl.mbadziong.airport.Airport
import pl.mbadziong.drone.Position

class DroneOperatorTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "Drone operator actor" must {

    "be able to list all owned drones" in {
      val droneCount               = 5
      val droneOperatorActor       = spawn(DroneOperator("Mateusz", Airport(Position(0.0, 0.0))))
      val createdDronesProbe       = createTestProbe[ReplyOwnedDrones]()
      val createDroneOperatorProbe = createTestProbe[DroneFleetCreated]()

      droneOperatorActor ! PrepareDroneFleet(droneCount, createDroneOperatorProbe.ref)
      createDroneOperatorProbe.expectMessageType[DroneFleetCreated]

      droneOperatorActor ! RequestOwnedDrones(1L, "Mateusz", createdDronesProbe.ref)
      createdDronesProbe.expectMessage(ReplyOwnedDrones(1L, Set(1, 2, 3, 4, 5)))
    }

    "be able to ignore requests for wrong operator" in {
      val droneCount               = 5
      val droneOperatorActor       = spawn(DroneOperator("Mateusz", Airport(Position(0.0, 0.0))))
      val createdDronesProbe       = createTestProbe[ReplyOwnedDrones]()
      val createDroneOperatorProbe = createTestProbe[DroneFleetCreated]()

      droneOperatorActor ! PrepareDroneFleet(droneCount, createDroneOperatorProbe.ref)
      createDroneOperatorProbe.expectMessageType[DroneFleetCreated]

      droneOperatorActor ! RequestOwnedDrones(1L, "Test", createdDronesProbe.ref)
      createdDronesProbe.expectNoMessage()
    }

    "be able to list active drones after one shuts down" in {
      val droneCount         = 5
      val droneOperatorActor = spawn(DroneOperator("Mateusz", Airport(Position(0.0, 0.0))))
      val probe              = createTestProbe[DroneFleetCreated]()

      droneOperatorActor ! PrepareDroneFleet(droneCount, probe.ref)
      val response        = probe.receiveMessage()
      val firstActorDrone = response.droneActors(1)
      firstActorDrone ! Drone.TurnOffDrone
      probe.expectTerminated(firstActorDrone, probe.remainingOrDefault)

      val ownedDronesProbe = createTestProbe[ReplyOwnedDrones]()
      droneOperatorActor ! RequestOwnedDrones(1L, "Mateusz", ownedDronesProbe.ref)

      ownedDronesProbe.expectMessage(ReplyOwnedDrones(1L, Set(2, 3, 4, 5)))
    }

    "be able to collect state of all owned drones" in {
      val droneAddedProbe = createTestProbe[DroneAddedToFleet]()
      val operatorActor   = spawn(DroneOperator("Mateusz", Airport(Position(0.0, 0.0))))

      operatorActor ! AddDroneToFleet(1, droneAddedProbe.ref)
      val drone1Actor = droneAddedProbe.receiveMessage().drone

      operatorActor ! AddDroneToFleet(2, droneAddedProbe.ref)
      val drone2Actor = droneAddedProbe.receiveMessage().drone

      val positionProbe = createTestProbe[PositionSet]()
      drone1Actor ! SetPosition(1, Position(1, 1), positionProbe.ref)
      positionProbe.expectMessage(PositionSet(requestId = 1))
      drone2Actor ! SetPosition(2, Position(2, 2), positionProbe.ref)
      positionProbe.expectMessage(PositionSet(requestId = 2))

      val allStateProbe = createTestProbe[RespondFleetState]()
      operatorActor ! RequestFleetState(requestId = 3, operator = "Mateusz", allStateProbe.ref)
      allStateProbe.expectMessage(
        RespondFleetState(
          requestId = 3,
          state = Map(1L -> InFlight(Position(1.0, 1.0)), 2L -> InFlight(Position(2.0, 2.0)))
        )
      )
    }
  }
}
