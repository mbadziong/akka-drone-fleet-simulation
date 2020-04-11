package pl.mbadziong.flight

sealed trait FlightResponse
final case class FlightCompleted(flightId: Long)               extends FlightResponse
final case class FlightDenied(flightId: Long, message: String) extends FlightResponse
final case class FlightAccepted(flightId: Long)                extends FlightResponse
