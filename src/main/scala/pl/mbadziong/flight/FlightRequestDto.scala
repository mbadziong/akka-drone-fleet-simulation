package pl.mbadziong.flight

import pl.mbadziong.drone.Position

case class FlightRequestDto(destination: Position)
case class FlightRequest(id: Long, destination: Position)
