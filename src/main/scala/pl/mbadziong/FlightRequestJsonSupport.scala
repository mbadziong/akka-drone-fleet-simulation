package pl.mbadziong

import pl.mbadziong.drone.Position
import pl.mbadziong.flight.FlightRequest
import spray.json.DefaultJsonProtocol

object FlightRequestJsonSupport {
  import DefaultJsonProtocol._
  implicit val positionFormat      = jsonFormat2(Position)
  implicit val flightRequestFormat = jsonFormat2(FlightRequest)
}
