package pl.mbadziong

import akka.actor.ActorSystem
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.duration._

class SupervisorRoutesSpec extends WordSpec with Matchers with ScalaFutures with ScalatestRouteTest {

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
  }
}