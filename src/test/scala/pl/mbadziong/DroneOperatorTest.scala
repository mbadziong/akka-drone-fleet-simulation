package pl.mbadziong

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike
import pl.mbadziong.DroneOperator.{PrepareDroneFleet, ReplyOwnedDrones, RequestOwnedDrones}
import pl.mbadziong.DroneSimulationSupervisor.DroneFleetCreated

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
  }
}
