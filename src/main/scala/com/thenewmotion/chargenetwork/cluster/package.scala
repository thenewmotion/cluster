package com.thenewmotion.chargenetwork

import akka.cluster.Member
import akka.actor.Address

/**
 * @author Yaroslav Klymko
 */
package object cluster {
  type ChargerId = String

  implicit class RichMember(val self: Member) extends AnyVal {
    def isGateway: Boolean = self.roles contains "gateway"
    def isRuntimes: Boolean = self.roles contains "runtimes"
  }

  implicit class RichAddress(val self: Address) extends AnyVal {
    def display: String = self.port.fold("")(_.toString)
  }
}
