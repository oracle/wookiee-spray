package com.webtrends.harness.component.spray.websocket

import akka.actor.Cancellable
import com.webtrends.harness.app.HActor
import com.webtrends.harness.command.CommandBean
import spray.can.{Http, websocket}
import spray.can.websocket.FrameCommandFailed
import spray.can.websocket.frame.{Frame, PingFrame, PongFrame}

import scala.concurrent.duration._
import scala.util.{Success, Try}

// Trait used to deal with back pressure on a websocket.
// If there are two consecutive messages to be pushed with the same WSDupeTypeId, only send the last one.
trait WSDupeTypeId

final case class Push(msg: String, dupeType: Option[WSDupeTypeId] = None)
final case class PushFrame(f: Frame)
final case class SetBean(bean: Option[CommandBean])

/**
 * Created by wallinm on 4/3/15.
 */
trait WebSocketWorker extends HActor {

  override def receive = health orElse businessLogic orElse closeLogic

  private var heartbeat: Option[Cancellable] = None

  var bean: Option[CommandBean] = None

  override def preStart(): Unit = {
    heartbeat = Some(context.system.scheduler.schedule(5 second, 5 second,
      context.parent, PushFrame(PingFrame()))(context.system.dispatcher)
    )
  }

  def getCommandBean() = {
    bean match {
      case Some(b) => b
      case None => new CommandBean()
    }
  }

  def businessLogic: Receive = {
    case p: Push =>
      log.debug("Got message " + p.msg)
      context.parent ! p

    case p: PongFrame =>  // Swallow message

    case SetBean(b) =>
      bean = b
      sender() ! Success

    case x: FrameCommandFailed =>
      log.error("Server frame command failed", x)

    case websocket.UpgradedToWebSocket =>
      log.debug("Server upgraded to WebSocket")    
  }

  def closeLogic: Receive = {
    case ev: Http.ConnectionClosed =>
      context.stop(self)
      log.debug("Server connection closed on event: {}", ev)
    
    case x => log.debug("Server received unknown message " + x)
  }

  override def postStop(): Unit = {
    heartbeat.foreach(_.cancel())
    super.postStop()
  }
}
