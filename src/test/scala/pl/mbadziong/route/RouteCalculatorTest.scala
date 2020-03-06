package pl.mbadziong.route

import org.scalatest.wordspec.AnyWordSpec
import pl.mbadziong.drone.Position


class RouteCalculatorTest extends AnyWordSpec {
  "A RouteCalculator" when {
    "meter per second is positive" should {
      "respond with calculated points" in {
        val response = RouteCalculator.getPoints(Position(54.4066489,18.5768217), Position(54.406056,18.572152), 10)
        assert(response.size == 32)
      }
    }

    "meter per second is not positive" should {
      "produce IllegalArgumentException" in {
        assertThrows[IllegalArgumentException] {
          RouteCalculator.getPoints(Position(54.4066489,18.5768217), Position(54.406056,18.572152), 0)
        }
      }
    }
  }
}
