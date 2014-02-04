package com.thenewmotion.chargenetwork.cluster

import akka.actor._
import akka.cluster.ClusterEvent._

/**
 * @author Yaroslav Klymko
 */
class ClusterLogger extends Actor with ActorLogging {
  def receive = {
    case state: CurrentClusterState =>
      log.info("Current members: {}", state.members)
    case MemberUp(member) =>
      log.info("Member is Up: {}", member)
    case UnreachableMember(member) =>
      log.info("Member detected as unreachable: {}", member)
    case MemberRemoved(member, _) =>
      log.info("Member is Removed: {}", member)
//    case event: ClusterDomainEvent =>
//      log.info(event.toString)
  }
}
