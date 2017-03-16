package com.webtrends.harness.component.spray.websocket

import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.locks.ReentrantLock

import akka.actor._
import akka.dispatch._
import com.typesafe.config.Config

/**
 * Copied from https://github.com/smootoo/simple-spray-websockets/blob/master/src/main/scala/org/suecarter/websocket/PriorityUnboundedDequeMailbox.scala
 *
 * Specialist priority (user provides the rules), unbounded, deque
 * (can be used for Stashing) mailbox.
 *
 * Very useful for messages of high priority, such as `Ack`s in I/O
 * situations.
 *
 * Based on UnboundedDequeBasedMailbox from Akka.
 */
abstract class PriorityUnboundedDequeMailbox extends MailboxType with ProducesMessageQueue[UnboundedDequeBasedMailbox.MessageQueue] {
  def this(settings: ActorSystem.Settings, config: Config) = this()

  final override def create(owner: Option[ActorRef], system: Option[ActorSystem]): MessageQueue =
    new PriorityUnboundedDequeMailbox.MessageQueue(priority, isDupe)

  /**
   * When true, the queue will place this envelope at the front of the
   * queue (as if it was just stashed).
   */
  def priority(e: Envelope): Boolean

  /**
    * When true, the new envelope will replace the last envelope in the queue
    * When false, it will be added to the end
    */
  def isDupe(lastEnv: Envelope, newEnv: Envelope): Boolean
}

/**
 * Copied from https://github.com/smootoo/simple-spray-websockets/blob/master/src/main/scala/org/suecarter/websocket/PriorityUnboundedDequeMailbox.scala
 */
object PriorityUnboundedDequeMailbox {

  class MessageQueue(priority: Envelope => Boolean, isDupe: (Envelope, Envelope) => Boolean) extends LinkedBlockingDeque[Envelope] with UnboundedDequeBasedMessageQueue {
    final val queue = this

    val elock = new ReentrantLock()

    override def enqueue(receiver: ActorRef, handle: Envelope): Unit = {
      elock.synchronized {
        if (priority(handle)) {
          super.enqueueFirst(receiver, handle)
        } else if (size > 0 && isDupe(handle, queue.peekLast())) {
          super.removeLast()
          super.enqueue(receiver, handle)
        }
        else {
          super.enqueue(receiver, handle)
        }
      }
    }

    override def dequeue(): Envelope = {
      elock.synchronized {
        super.dequeue()
      }
    }
  }

}
