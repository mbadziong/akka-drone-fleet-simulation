package pl.mbadziong

import pl.mbadziong.drone.Position
import pl.mbadziong.flight.FlightRequestDto
import spray.json.DefaultJsonProtocol

object FlightRequestJsonSupport {
  import DefaultJsonProtocol._
  implicit val positionFormat      = jsonFormat2(Position)
  implicit val flightRequestFormat = jsonFormat1(FlightRequestDto)
}
