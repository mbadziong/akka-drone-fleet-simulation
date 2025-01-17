package pl.mbadziong.route

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import pl.mbadziong.common.Position
import pl.mbadziong.operator.DroneOperator

object RouteProvider {
  sealed trait Command
  final case class RouteRequest(requestId: Long, from: Position, to: Position, mps: Int, replyTo: ActorRef[RouteResponse]) extends Command
  final case class RouteResponse(requestId: Long, route: List[Position])                                                   extends DroneOperator.Command

  def apply(): Behavior[Command] = routeProvider()

  private def routeProvider(): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match {
        case RouteRequest(requestId, from, to, mps, replyTo) =>
          val calculatedRoute = RouteCalculator.getPoints(from, to, mps)
          val finalRoute      = calculatedRoute ++ calculatedRoute.reverse
          context.log.info(s"calculated route for request $requestId is $calculatedRoute")
          replyTo ! RouteResponse(requestId, finalRoute)
          Behaviors.same
        case _ => Behaviors.same
      }
    }
}
