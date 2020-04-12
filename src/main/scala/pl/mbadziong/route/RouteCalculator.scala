package pl.mbadziong.route

import pl.mbadziong.common.Position

import scala.collection.mutable.ListBuffer

private object RouteCalculator {
  private val RADIUS_OF_EARTH = 6371000 // radius of earth in m

  def getPoints(startPoint: Position, endPoint: Position, meterPerSecond: Int): List[Position] = {
    if (meterPerSecond <= 0) throw new IllegalArgumentException("meterPerSecond less or equals 0")
    val azimuth = calculateBearing(startPoint, endPoint)
    getLocations(meterPerSecond, azimuth, startPoint, endPoint)
  }

  private def getLocations(interval: Int, azimuth: Double, start: Position, end: Position): List[Position] = {
    val distance         = getPathLength(start, end)
    val pointsToGenerate = distance.toInt / interval
    println(s"generating $pointsToGenerate middle points for distance of $distance meters with interval of $interval meters per second")
    var coveredDist = interval
    val coords      = new ListBuffer[Position]
    coords += Position(start.lat, start.lon)
    (1 to pointsToGenerate)
      .foreach(_ => {
        val coord = getDestinationLatLng(start.lat, start.lon, azimuth, coveredDist)
        coveredDist += interval
        coords += coord
      })
    coords += Position(end.lat, end.lon)
    println(s"created $coords.size points")
    coords.toList
  }

  /**
    * calculates the distance between two lat, long coordinate pairs
    */
  private def getPathLength(start: Position, end: Position): Double = {
    val lat1Rads = Math.toRadians(start.lat)
    val lat2Rads = Math.toRadians(end.lat)
    val deltaLat = Math.toRadians(end.lat - start.lat)
    val deltaLng = Math.toRadians(end.lon - start.lon)
    val a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) + Math.cos(lat1Rads) * Math.cos(lat2Rads) * Math.sin(deltaLng / 2) * Math.sin(
      deltaLng / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    RADIUS_OF_EARTH * c
  }

  /**
    * returns the lat an long of destination point given the start lat, long, aziuth, and distance
    */
  private def getDestinationLatLng(lat: Double, lng: Double, azimuth: Double, distance: Double): Position = {
    val radiusKm = RADIUS_OF_EARTH / 1000.0 //Radius of the Earth in km
    val brng     = Math.toRadians(azimuth) //Bearing is degrees converted to radians.
    val d        = distance / 1000 //Distance m converted to km
    val lat1     = Math.toRadians(lat) //Current dd lat point converted to radians
    val lon1     = Math.toRadians(lng) //Current dd long point converted to radians
    var lat2     = Math.asin(Math.sin(lat1) * Math.cos(d / radiusKm) + Math.cos(lat1) * Math.sin(d / radiusKm) * Math.cos(brng))
    var lon2 = lon1 + Math.atan2(Math.sin(brng) * Math.sin(d / radiusKm) * Math.cos(lat1),
                                 Math.cos(d / radiusKm) - Math.sin(lat1) * Math.sin(lat2))
    //convert back to degrees
    lat2 = Math.toDegrees(lat2)
    lon2 = Math.toDegrees(lon2)
    Position(lat2, lon2)
  }

  /**
    * calculates the azimuth in degrees from start point to end point");
    * double startLat = Math.toRadians(start.getLat());
    */
  private def calculateBearing(start: Position, end: Position): Double = {
    val startLat  = Math.toRadians(start.lat)
    val startLong = Math.toRadians(start.lon)
    val endLat    = Math.toRadians(end.lat)
    val endLong   = Math.toRadians(end.lon)
    var dLong     = endLong - startLong
    val dPhi      = Math.log(Math.tan((endLat / 2.0) + (Math.PI / 4.0)) / Math.tan((startLat / 2.0) + (Math.PI / 4.0)))
    if (Math.abs(dLong) > Math.PI)
      if (dLong > 0.0) dLong = -(2.0 * Math.PI - dLong)
      else dLong = 2.0 * Math.PI + dLong
    (Math.toDegrees(Math.atan2(dLong, dPhi)) + 360.0) % 360.0
  }

}
