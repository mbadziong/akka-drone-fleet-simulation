package pl.mbadziong.flight

import pl.mbadziong.drone.Position

case class FlightRequest(id: Long, route: List[Position])
