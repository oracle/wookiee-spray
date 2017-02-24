package com.webtrends.harness.component.spray.websocket

import akka.actor.{Actor, ActorSystem}
import akka.dispatch.Envelope
import akka.testkit.TestKit
import org.scalatest.{MustMatchers, WordSpecLike}

class PriorityUnboundedDequeMailboxSpec extends TestKit(ActorSystem("PriorityUnboundedDequeMailbox")) with WordSpecLike with MustMatchers {

  "PriorityUnboundedDequeMailbox " should {

    "prevent consecutive items" in {

      val queue = HighPriorityAckMailbox(system.settings, null).create(None, None)

      case object DupeId1 extends WSDupeTypeId
      case object DupeId2 extends WSDupeTypeId
      case object DupeId3 extends WSDupeTypeId

      queue.enqueue(null, Envelope(Push("1", Some(DupeId1)), Actor.noSender, system))
      queue.enqueue(null, Envelope(Push("2", Some(DupeId2)), Actor.noSender, system))
      queue.enqueue(null, Envelope(Push("3", Some(DupeId3)), Actor.noSender, system))
      queue.enqueue(null, Envelope(Push("4", Some(DupeId2)), Actor.noSender, system))
      queue.enqueue(null, Envelope(Push("5 - Removed", Some(DupeId1)), Actor.noSender, system))
      queue.enqueue(null, Envelope(Push("6 - Removed", Some(DupeId1)), Actor.noSender, system))
      queue.enqueue(null, Envelope(Push("7", Some(DupeId1)), Actor.noSender, system))
      queue.enqueue(null, Envelope(Push("8 - Removed", Some(DupeId2)), Actor.noSender, system))
      queue.enqueue(null, Envelope(Push("9", Some(DupeId2)), Actor.noSender, system))

      queue.numberOfMessages mustEqual 6
      var values = for (n <- 0 until queue.numberOfMessages) yield queue.dequeue().message.asInstanceOf[Push].msg
      values mustEqual Seq("1", "2", "3", "4", "7", "9")
      queue.numberOfMessages mustEqual 0
    }
  }
}
