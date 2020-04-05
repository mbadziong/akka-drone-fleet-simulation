package pl.mbadziong.route

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike
import pl.mbadziong.drone.Position
import pl.mbadziong.route.RouteProvider.{RouteRequest, RouteResponse}

class RouteProviderTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "Route Provider actor" must {

    "calculate route for given points and speed" in {
      val routeProviderActor = spawn(RouteProvider())
      val routeResponseProbe = createTestProbe[RouteResponse]()

      routeProviderActor ! RouteRequest(1, Position(54.4066489, 18.5768217), Position(54.406056, 18.572152), 10, routeResponseProbe.ref)

      val response = routeResponseProbe.receiveMessage

      response.requestId should ===(1)
      response.route.size should ===(64)
    }
  }
}
