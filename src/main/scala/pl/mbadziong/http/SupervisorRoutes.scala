package pl.mbadziong.http

import java.util.concurrent.atomic.AtomicLong

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import akka.util.Timeout
import pl.mbadziong.airport.ARKONSKA_GDANSK_AIRPORT
import pl.mbadziong.flight.{FlightAccepted, FlightDenied, FlightRequest, FlightRequestDto}
import pl.mbadziong.supervisor.SimulationSupervisor
import pl.mbadziong.supervisor.SimulationSupervisor._

import scala.concurrent.Future

class SupervisorRoutes(supervisorActor: ActorRef[SimulationSupervisor.Command])(implicit val system: ActorSystem[_]) extends Directives {

  import FlightRequestJsonSupport._
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

  private implicit val timeout: Timeout = Timeout.create(system.settings.config.getDuration("simulator.routes.ask-timeout"))
  private val requestIdCounter          = new AtomicLong()

  def createDroneOperator(operatorName: String): Future[CreatedDroneOperator] =
    supervisorActor.ask(CreateDroneOperator(operatorName, ARKONSKA_GDANSK_AIRPORT, _))

  def getFleetState(requestId: Long, operatorName: String): Future[RespondFleetState] =
    supervisorActor.ask(RequestFleetState(requestId, operatorName, _))

  def generateFleet(operatorName: String, count: Int): Future[DroneFleetCreated] =
    supervisorActor.ask(GenerateDrones(operatorName, count, _))

  def simulateFlight(operatorName: String, flightRequest: FlightRequestDto): Future[HandleFlightResponse] =
    supervisorActor.ask(HandleFlightRequest(FlightRequest(requestIdCounter.getAndIncrement(), flightRequest.destination), operatorName, _))

  val supervisorRoutes: Route =
    pathPrefix("operator" / Segment) { operatorName =>
      path("add") {
        post {
          parameters(Symbol("count").as[Int]) { count =>
            {
              onSuccess(generateFleet(operatorName, count)) { _ =>
                complete((StatusCodes.Created, s"done, created $count drones for operator $operatorName"))
              }
            }
          }
        }
      } ~
        path("flight") {
          post {
            entity(as[FlightRequestDto]) { flightRequest =>
              onSuccess(simulateFlight(operatorName, flightRequest)) { response =>
                response.flightResponse match {
                  case FlightAccepted(flightId) =>
                    complete((StatusCodes.Accepted, s"flight accepted, request handled by $operatorName, id: $flightId"))
                  case FlightDenied(flightId, message) =>
                    complete((StatusCodes.Accepted, s"flight denied, request handled by $operatorName, id: $flightId, msg: $message"))
                }
              }
            }
          }
        } ~
        pathEnd {
          {
            concat(
              put {
                onSuccess(createDroneOperator(operatorName)) { _ =>
                  complete((StatusCodes.Created, s"done, created operator $operatorName"))
                }
              },
              get {
                onSuccess(getFleetState(requestIdCounter.getAndIncrement(), operatorName)) { response =>
                  complete((StatusCodes.OK, response.state.toString()))
                }
              }
            )
          }
        }
    }
}
