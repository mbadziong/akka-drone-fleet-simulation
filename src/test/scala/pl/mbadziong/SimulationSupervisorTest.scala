package pl.mbadziong

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike
import pl.mbadziong.DroneOperator.{ReplyFleet, RequestFleet}
import pl.mbadziong.SimulationSupervisor._
import pl.mbadziong.airport.Airport
import pl.mbadziong.drone.Position
import pl.mbadziong.flight.{FlightCompleted, FlightRequest}

class SimulationSupervisorTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "Simulation supervisor actor" should {

    "Allow to create drone operator" in {
      val createdDroneOperatorProbe = createTestProbe[CreatedDroneOperator]()
      val supervisorActor           = spawn(SimulationSupervisor())
      val operatorName              = "test"
      val airport                   = Airport(Position(1, 1))

      supervisorActor ! CreateDroneOperator(operatorName, airport, createdDroneOperatorProbe.ref)
      createdDroneOperatorProbe.expectMessageType[CreatedDroneOperator]
    }

    "Generate drones for created operator" in {
      val createdDroneOperatorProbe = createTestProbe[CreatedDroneOperator]()
      val supervisorActor           = spawn(SimulationSupervisor())
      val operatorName              = "test"
      val airport                   = Airport(Position(1, 1))

      supervisorActor ! CreateDroneOperator(operatorName, airport, createdDroneOperatorProbe.ref)
      createdDroneOperatorProbe.expectMessageType[CreatedDroneOperator]

      val droneFleetCreatedProbe = createTestProbe[DroneFleetCreated]()
      supervisorActor ! GenerateDrones(operatorName, 5, droneFleetCreatedProbe.ref)
      droneFleetCreatedProbe.expectMessageType[DroneFleetCreated]
    }

    "Allow to see operator's fleet" in {
      val createdDroneOperatorProbe = createTestProbe[CreatedDroneOperator]()
      val supervisorActor           = spawn(SimulationSupervisor())
      val operatorName              = "test"
      val airport                   = Airport(Position(1, 1))

      supervisorActor ! CreateDroneOperator(operatorName, airport, createdDroneOperatorProbe.ref)
      createdDroneOperatorProbe.expectMessageType[CreatedDroneOperator]

      val droneFleetCreatedProbe = createTestProbe[DroneFleetCreated]()
      supervisorActor ! GenerateDrones(operatorName, 5, droneFleetCreatedProbe.ref)
      droneFleetCreatedProbe.expectMessageType[DroneFleetCreated]

      val replyOwnedDronesProbe = createTestProbe[ReplyFleet]()
      supervisorActor ! RequestFleet(1L, operatorName, replyOwnedDronesProbe.ref)
      replyOwnedDronesProbe.expectMessage(
        ReplyFleet(1L, Set(0, 1, 2, 3, 4))
      )
    }

    "Show fleet state for operator" in {
      val createdDroneOperatorProbe = createTestProbe[CreatedDroneOperator]()
      val supervisorActor           = spawn(SimulationSupervisor())
      val operatorName              = "test"
      val airport                   = Airport(Position(1, 1))

      supervisorActor ! CreateDroneOperator(operatorName, airport, createdDroneOperatorProbe.ref)
      createdDroneOperatorProbe.expectMessageType[CreatedDroneOperator]

      val droneFleetCreatedProbe = createTestProbe[DroneFleetCreated]()
      supervisorActor ! GenerateDrones(operatorName, 5, droneFleetCreatedProbe.ref)
      droneFleetCreatedProbe.expectMessageType[DroneFleetCreated]

      val respondFleetStateProbe = createTestProbe[RespondFleetState]()
      supervisorActor ! RequestFleetState(1, operatorName, respondFleetStateProbe.ref)
      respondFleetStateProbe.expectMessage(
        RespondFleetState(
          requestId = 1,
          state = Map(
            0L -> ReadyToFlight,
            1L -> ReadyToFlight,
            2L -> ReadyToFlight,
            3L -> ReadyToFlight,
            4L -> ReadyToFlight
          )
        )
      )
    }

    "Process flight request for operator" in {
      val createdDroneOperatorProbe = createTestProbe[CreatedDroneOperator]()
      val supervisorActor           = spawn(SimulationSupervisor())
      val operatorName              = "test"
      val airport                   = Airport(Position(54.406001, 18.575956))

      supervisorActor ! CreateDroneOperator(operatorName, airport, createdDroneOperatorProbe.ref)
      createdDroneOperatorProbe.expectMessageType[CreatedDroneOperator]

      val droneFleetCreatedProbe = createTestProbe[DroneFleetCreated]()
      supervisorActor ! GenerateDrones(operatorName, 5, droneFleetCreatedProbe.ref)
      droneFleetCreatedProbe.expectMessageType[DroneFleetCreated]

      val handleFlightResponseProbe = createTestProbe[HandleFlightResponse]()
      supervisorActor ! HandleFlightRequest(FlightRequest(1L, Position(54.406335, 18.581467)), operatorName, handleFlightResponseProbe.ref)
      handleFlightResponseProbe.expectMessage(HandleFlightResponse(FlightCompleted(1)))
    }
  }
}
