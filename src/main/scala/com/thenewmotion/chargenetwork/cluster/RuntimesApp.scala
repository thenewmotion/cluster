package com.thenewmotion.chargenetwork.cluster

import akka.actor._
import akka.cluster.{Member, Cluster}
import akka.cluster.ClusterEvent._

object RuntimesApp extends App {
  val system = ActorSystem("cluster")

  val cluster = Cluster(system)

  val clusterLogger = system.actorOf(Props[ClusterLogger], name = "clusterLogger")
  cluster.subscribe(clusterLogger, classOf[ClusterDomainEvent])

  val runtimes = system.actorOf(Props[RuntimesSupervisor], name = "runtimes")
  cluster.subscribe(runtimes, classOf[ClusterDomainEvent])
}

class RuntimesSupervisor extends Actor with ActorLogging {

  import RuntimesSupervisor._

  var runtimes: Map[ChargerId, ActorRef] = Map()
  var gateways: Set[Member] = Set()

  def receive = {
    case state: CurrentClusterState =>
      gateways = state.members.filter(_.isGateway)
      notifyGateways()

    case MemberUp(node) if node.isGateway =>
      gateways = gateways + node
      notifyGateway(node)

    case MemberRemoved(node) if node.isGateway =>
      gateways = gateways - node

    case req@StatusAvailableReq(chargerId) =>
      val runtime = context.actorOf(Props(classOf[Runtime], chargerId))
      runtime forward req
      runtimes = runtimes + (chargerId -> runtime)
      notifyGateways()
  }

  def notifyGateway(node: Member) {
    val gateway = context.actorSelection(gatewayPath(node.address))
    gateway ! Sync(runtimes)
  }

  def notifyGateways() {
    gateways.foreach(notifyGateway)
  }

  def gatewayPath(address: Address): ActorPath = RootActorPath(address) / "user" / "gateway"
}

object RuntimesSupervisor {
  case class Sync(runtimes: Map[ChargerId, ActorRef])
}

class Runtime(chargerId: String) extends Actor with ActorLogging {

  override def preStart() {
    log.info("{}: runtime created", chargerId)
  }

  def receive = {
    case StatusAvailableReq(_) => sender ! OkRes
  }
}