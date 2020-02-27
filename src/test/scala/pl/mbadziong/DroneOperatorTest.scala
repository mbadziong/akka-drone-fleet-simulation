package pl.mbadziong

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike
import pl.mbadziong.Drone.{PositionSet, SetPosition}
import pl.mbadziong.DroneOperator.{AddDroneToFleet, DroneAddedToFleet, PrepareDroneFleet, ReplyOwnedDrones, RequestOwnedDrones}
import pl.mbadziong.DroneSimulationSupervisor.{DroneFleetCreated, InFlight, RequestFleetState, RespondFleetState}
import pl.mbadziong.drone.Position

class DroneOperatorTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "Drone operator actor" must {

    "be able to list all owned drones" in {
      val droneCount               = 5
      val droneOperatorActor       = spawn(DroneOperator("Mateusz"))
      val createdDronesProbe       = createTestProbe[ReplyOwnedDrones]()
      val createDroneOperatorProbe = createTestProbe[DroneFleetCreated]()

      droneOperatorActor ! PrepareDroneFleet(droneCount, createDroneOperatorProbe.ref)
      createDroneOperatorProbe.expectMessageType[DroneFleetCreated]

      droneOperatorActor ! RequestOwnedDrones(1L, "Mateusz", createdDronesProbe.ref)
      createdDronesProbe.expectMessage(ReplyOwnedDrones(1L, Set(1, 2, 3, 4, 5)))
    }

    "be able to list active drones after one shuts down" in {
      val droneCount         = 5
      val droneOperatorActor = spawn(DroneOperator("Mateusz"))
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

    "be able to collect state of all owned drones" {
      val droneAddedProbe = createTestProbe[DroneAddedToFleet]()
      val operatorActor = spawn(DroneOperator("Mateusz"))

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
          state = Map(1 -> InFlight(Position(1, 1)), 2 -> InFlight(Position(2,2)))
        )
      )
    }
  }
}
