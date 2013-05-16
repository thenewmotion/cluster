package com.thenewmotion.chargenetwork.cluster

import akka.actor._
import akka.actor.RootActorPath
import scala.util.Random
import concurrent.duration._
import scala.collection.JavaConverters._

/**
 * @author Yaroslav Klymko
 */
object LoadBalancerApp extends App {
  val system = ActorSystem("loadbalancer")
  val loadBalancer = system.actorOf(Props[LoadBalancer])

  (0 to 10).foreach {
    x =>
      val chargerId = s"charger$x"
      system.actorOf(Props(classOf[ChargerActor], chargerId, loadBalancer), chargerId)
  }
}

class LoadBalancer extends Actor {
  val gateways: Iterator[ActorSelection] = {
    val uris = context.system.settings.config.getStringList("chargenetwork.gateways").asScala
    val gateways = uris.map {
      uri =>
        val address = AddressFromURIString(uri)
        context.actorSelection(RootActorPath(address) / "user" / "gateway")
    }

    def loop: Stream[ActorSelection] = gateways.toStream #::: loop
    loop.toIterator
  }

  def receive = {
    case req: StatusAvailableReq =>
      val gateway = gateways.next()
      gateway.tell(req, sender)
  }
}


class ChargerActor(id: ChargerId, loadbalancer: ActorRef) extends Actor with ActorLogging {

  import context.dispatcher

  override def preStart() {
    val interval = Random.nextInt(10)
    context.system.scheduler.schedule(interval seconds, interval seconds, self, Tick)
  }

  def receive = {
    case Tick =>
//      log.info(s"$id: Available")
      loadbalancer ! StatusAvailableReq(id)

    case OkRes =>
      log.info(s"$id: OK from ${sender.path.address.display}")
  }

  case object Tick

}