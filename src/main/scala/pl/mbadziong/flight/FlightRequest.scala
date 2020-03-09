package pl.mbadziong.flight

import pl.mbadziong.drone.Position

case class FlightRequest(id: Int, route: List[Position])
