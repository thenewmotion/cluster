package com.thenewmotion.chargenetwork.cluster

import akka.actor._
import akka.cluster.{Member, Cluster}
import akka.cluster.ClusterEvent._
import concurrent.duration._
import com.thenewmotion.chargenetwork.cluster.RuntimesSupervisor.Sync


/**
 * @author Yaroslav Klymko
 */

object GatewayApp extends App {
  val system = ActorSystem("cluster")

  val cluster = Cluster(system)

  val gateway = system.actorOf(Props[GatewayActor], "gateway")
  cluster.subscribe(gateway, classOf[ClusterDomainEvent])

  val clusterLogger = system.actorOf(Props[ClusterLogger], name = "clusterLogger")
  cluster.subscribe(clusterLogger, classOf[ClusterDomainEvent])
}

class GatewayActor extends Actor with ActorLogging {

  import context.dispatcher

  var runtimes: Map[ChargerId, ActorRef] = Map()
  var runtimesNodes: Set[Member] = Set()

  def receive = {
    case req@StatusAvailableReq(chargerId) =>
      val responseActor = context.actorOf(Props(classOf[ConnectionActor], sender, req))
      context.system.scheduler.scheduleOnce(3 seconds, responseActor, PoisonPill)

      runtimes.get(chargerId) match {
        case Some(runtime) =>
          log.info("{}: forwarding to {}", chargerId, req, runtime.path.address.display)
          runtime.tell(req, responseActor)
        case None => runtimesNodeForNewCharger(chargerId) match {
          case None => log.warning("{}: no runtimes found", chargerId)
          case Some(node) =>
            val path = RootActorPath(node.address) / "user" / "runtimes"
            val runtimes = context.actorSelection(path)
            log.info("{}: forwarding to {}", chargerId, req, path.address.display)
            runtimes.tell(req, responseActor)
        }
      }

    case MemberRemoved(node) if node.isRuntimes =>
      runtimesNodes = runtimesNodes - node
      runtimes = runtimes.filterNot {
        case (chargerId, actor) => actor.path.address == node.address
      }

    case MemberUp(node) if node.isRuntimes =>
      runtimesNodes = runtimesNodes + node

    case state: CurrentClusterState =>
      runtimesNodes = state.members.filter(_.isRuntimes)

    case Sync(xs) => runtimes = runtimes ++ xs
  }

  def runtimesNodeForNewCharger(chargerId: ChargerId): Option[Member] = {
    if (runtimesNodes.isEmpty) None
    else Some({
      val xs = runtimes.values.map(_.path.address)
      runtimesNodes.minBy(x => xs.count(_ == x.address))
    })
  }
}

class ConnectionActor(connection: ActorRef, req: StatusAvailableReq) extends Actor with ActorLogging {
  def receive = {
    case res@OkRes =>
      log.info("{} received response from {}", req.chargerId, sender.path)
      connection forward res
      context.stop(self)
  }
}