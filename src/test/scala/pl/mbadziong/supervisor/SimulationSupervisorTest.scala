package pl.mbadziong.supervisor

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike
import pl.mbadziong.airport.{ARKONSKA_GDANSK_AIRPORT, Airport}
import pl.mbadziong.common.{NEAR_GDANSK_ARKONSKA_AIRPORT, Position}
import pl.mbadziong.flight.{FlightAccepted, FlightDenied, FlightRequest}
import pl.mbadziong.operator.DroneOperator.{ReplyFleet, RequestFleet}
import pl.mbadziong.supervisor.SimulationSupervisor._

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
      val airport                   = ARKONSKA_GDANSK_AIRPORT

      supervisorActor ! CreateDroneOperator(operatorName, airport, createdDroneOperatorProbe.ref)
      createdDroneOperatorProbe.expectMessageType[CreatedDroneOperator]

      val droneFleetCreatedProbe = createTestProbe[DroneFleetCreated]()
      supervisorActor ! GenerateDrones(operatorName, 5, droneFleetCreatedProbe.ref)
      droneFleetCreatedProbe.expectMessageType[DroneFleetCreated]

      val handleFlightResponseProbe = createTestProbe[HandleFlightResponse]()
      supervisorActor ! HandleFlightRequest(FlightRequest(1L, NEAR_GDANSK_ARKONSKA_AIRPORT), operatorName, handleFlightResponseProbe.ref)
      handleFlightResponseProbe.expectMessage(HandleFlightResponse(FlightAccepted(1)))
    }

    "Deny flight request for non existing operator" in {
      val createdDroneOperatorProbe = createTestProbe[CreatedDroneOperator]()
      val supervisorActor           = spawn(SimulationSupervisor())
      val operatorName              = "test"
      val airport                   = ARKONSKA_GDANSK_AIRPORT

      supervisorActor ! CreateDroneOperator(operatorName, airport, createdDroneOperatorProbe.ref)
      createdDroneOperatorProbe.expectMessageType[CreatedDroneOperator]

      val droneFleetCreatedProbe = createTestProbe[DroneFleetCreated]()
      supervisorActor ! GenerateDrones(operatorName, 5, droneFleetCreatedProbe.ref)
      droneFleetCreatedProbe.expectMessageType[DroneFleetCreated]

      val handleFlightResponseProbe = createTestProbe[HandleFlightResponse]()
      supervisorActor ! HandleFlightRequest(FlightRequest(1L, NEAR_GDANSK_ARKONSKA_AIRPORT),
                                            "otherOperatorName",
                                            handleFlightResponseProbe.ref)
      handleFlightResponseProbe.expectMessage(HandleFlightResponse(FlightDenied(1, "operator not found")))
    }
  }
}
