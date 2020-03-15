package pl.mbadziong

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{pathPrefix, _}
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import pl.mbadziong.SimulationSupervisor._
import pl.mbadziong.airport.Airport
import pl.mbadziong.drone.Position

import scala.concurrent.Future

class SupervisorRoutes(supervisorActor: ActorRef[SimulationSupervisor.Command])(implicit val system: ActorSystem[_]) {

  private implicit val timeout: Timeout = Timeout.create(system.settings.config.getDuration("my-app.routes.ask-timeout"))

  def createDroneOperator(operatorName: String): Future[CreatedDroneOperator] =
    supervisorActor.ask(CreateDroneOperator(operatorName, Airport(Position(0, 0)), _))

  def getFleetState(operatorName: String): Future[RespondFleetState] =
    supervisorActor.ask(RequestFleetState(1, operatorName, _))

  def generateFleet(operatorName: String, count: Int): Future[DroneFleetCreated] =
    supervisorActor.ask(GenerateDrones(operatorName, count, _))

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
        pathEnd {
          {
            concat(
              put {
                onSuccess(createDroneOperator(operatorName)) { _ =>
                  complete((StatusCodes.Created, s"done, created operator $operatorName"))
                }
              },
              get {
                onSuccess(getFleetState(operatorName)) { response =>
                  complete((StatusCodes.OK, response.state.toString()))
                }
              }
            )
          }
        }
    }
}