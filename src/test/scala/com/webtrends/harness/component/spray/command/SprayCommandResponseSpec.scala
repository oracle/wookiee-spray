package com.webtrends.harness.component.spray.command

import akka.actor.Props
import akka.testkit.TestActorRef
import com.webtrends.harness.command.{BaseCommandResponse, CommandBean}
import com.webtrends.harness.component.spray.route.RouteManager
import com.webtrends.harness.component.spray.routes.BaseTestCommand
import com.webtrends.harness.component.spray.{SprayManager, SprayTestConfig}
import com.webtrends.harness.service.test.TestHarness
import org.json4s.JObject
import org.scalatest.FunSuite
import spray.http._
import spray.routing.{Directives, HttpService}
import spray.testkit.ScalatestRouteTest

import scala.concurrent.Future

class SprayCommandResponseTestCommand extends BaseTestCommand {
  override def commandName: String = "SprayCommandResponseTest"
  override def path: String = "/test/SprayCommandResponse"
  val responseData = new JObject(List())

  override def execute[T:Manifest](bean: Option[CommandBean]): Future[BaseCommandResponse[T]] = {
    Future (new SprayCommandResponse[T](
      Some(responseData.asInstanceOf[T]),
      status = StatusCodes.Accepted,
      additionalHeaders = List (
        HttpHeaders.RawHeader("custom", "header")
      )
    ))
  }
}

class SprayCommandResponseSpec extends FunSuite
  with Directives
  with ScalatestRouteTest
  with HttpService {

  def actorRefFactory = system
  val sys = TestHarness(SprayTestConfig.config, None, Some(Map("wookiee-spray" -> classOf[SprayManager])))
  val testCommandRef = TestActorRef[SprayCommandResponseTestCommand](
    Props(new SprayCommandResponseTestCommand))(actorRefFactory)
  val testActor = testCommandRef.underlyingActor

  test("use specified status code and headers") {
    Get("/test/SprayCommandResponse") ~> RouteManager.getRoute("SprayCommandResponseTest_get").get ~> check {
      assert(status == StatusCodes.Accepted)
      assert(headers.exists(h => h.name == "custom" && h.value == "header"))
    }
  }
}
