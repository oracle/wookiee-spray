package com.webtrends.harness.component.spray.routes

import akka.testkit.TestActorRef
import com.webtrends.harness.command.{Command, CommandBean, CommandException}
import com.webtrends.harness.component.spray.command.SprayCommandResponse
import com.webtrends.harness.component.spray.route.{CommandRouteHandler, RouteManager, SprayGet}
import org.scalatest.{FunSuite, MustMatchers}
import spray.routing.{Directives, HttpService}
import spray.testkit.ScalatestRouteTest

import scala.concurrent.Future

class ExceptionCommand extends Command with SprayGet{
  override def commandName: String = "ExceptionCommand"
  override def path: String = "/test/ExceptionCommand"

  override def execute[T:Manifest](bean: Option[CommandBean]): Future[SprayCommandResponse[T]] = {
    throw CommandException("ExceptionCommand", new Exception("Do not leak this"))
  }
}

class ExceptionDebugCommand extends Command with SprayGet with CommandRouteHandler {
  override def commandName: String = "ExceptionDebugCommand"
  override def path: String = "/test/ExceptionDebugCommand"

  override def exceptionHandler = debugExceptionHandler

  override def execute[T:Manifest](bean: Option[CommandBean]): Future[SprayCommandResponse[T]] = {
    throw new Exception("Leak this")
  }
}

class CommandRouteHandlerSpec extends FunSuite with Directives with ScalatestRouteTest with HttpService with MustMatchers {
  def actorRefFactory = system

  TestActorRef[ExceptionCommand]
  TestActorRef[ExceptionDebugCommand]

  test("not include exception details in response") {
    Get("/test/ExceptionCommand") ~> RouteManager.getRoute("ExceptionCommand_get").get ~> check {
      status.intValue mustEqual 500
      responseAs[String] must not contain("Do not leak this")
    }
  }

  test("include exception details in response") {
    Get("/test/ExceptionDebugCommand") ~> RouteManager.getRoute("ExceptionDebugCommand_get").get ~> check {
      status.intValue mustEqual 500
      responseAs[String] must contain("Leak this")
    }
  }
}
