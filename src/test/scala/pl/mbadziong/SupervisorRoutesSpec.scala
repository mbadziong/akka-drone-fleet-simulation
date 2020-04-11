package pl.mbadziong

import akka.actor.ActorSystem
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}
import pl.mbadziong.airport.ARKONSKA_GDANSK_AIRPORT
import pl.mbadziong.drone.{NEAR_GDANSK_ARKONSKA_AIRPORT, Position}
import pl.mbadziong.flight.FlightRequest

import scala.concurrent.duration._

class SupervisorRoutesSpec extends WordSpec with Matchers with ScalaFutures with ScalatestRouteTest {

  import FlightRequestJsonSupport._
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

  lazy val testKit                                   = ActorTestKit()
  implicit def typedSystem                           = testKit.system
  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(10.seconds)
  override def createActorSystem(): akka.actor.ActorSystem =
    testKit.system.toClassic

  val simulationSupervisor = testKit.spawn(SimulationSupervisor())
  lazy val routes          = new SupervisorRoutes(simulationSupervisor).supervisorRoutes

  "SupervisorRoutes" should {
    "be able to add operator (PUT /operator/{name}" in {
      val operatorName = "test"
      val request      = Put(s"/operator/$operatorName")

      request ~> routes ~> check {
        status should ===(StatusCodes.Created)

        contentType should ===(ContentTypes.`text/plain(UTF-8)`)

        entityAs[String] should ===(s"done, created operator $operatorName")
      }
    }

    "be able to add drones to operator (POST /operator/{name}/add?count={count})" in {
      val operatorName = "test"
      val count        = 5
      val request      = Post(s"/operator/$operatorName/add?count=$count")

      request ~> routes ~> check {
        status should ===(StatusCodes.Created)

        contentType should ===(ContentTypes.`text/plain(UTF-8)`)

        entityAs[String] should ===(s"done, created $count drones for operator $operatorName")
      }
    }

    "be able to check operator state (GET /operator/{name}/state)" in {
      val operatorName = "test"

      val request = Get(s"/operator/$operatorName")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)

        contentType should ===(ContentTypes.`text/plain(UTF-8)`)

        entityAs[String] should ===(
          "HashMap(0 -> ReadyToFlight, 1 -> ReadyToFlight, 2 -> ReadyToFlight, 3 -> ReadyToFlight, 4 -> ReadyToFlight)")
      }
    }

    "be able to send flight request (POST /operator/{name}/flight)" in {
      val operatorName  = "test"
      val flightId      = 1
      val flightRequest = FlightRequest(flightId, NEAR_GDANSK_ARKONSKA_AIRPORT)
      val requestEntity = Marshal(flightRequest).to[MessageEntity].futureValue
      val request       = Post(s"/operator/$operatorName/flight").withEntity(requestEntity)

      request ~> routes ~> check {
        status should ===(StatusCodes.Accepted)

        contentType should ===(ContentTypes.`text/plain(UTF-8)`)

        entityAs[String] should ===(s"flight accepted, request handled by test, id: $flightId")
      }
    }
  }
}
