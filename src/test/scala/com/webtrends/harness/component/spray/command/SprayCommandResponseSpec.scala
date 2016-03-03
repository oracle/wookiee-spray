package com.webtrends.harness.component.spray.command

import akka.testkit.TestActorRef
import com.webtrends.harness.command.{BaseCommandResponse, CommandResponse, CommandBean}
import com.webtrends.harness.component.spray.route.RouteManager
import com.webtrends.harness.component.spray.routes.BaseTestCommand
import org.json4s.JObject
import org.specs2.mutable.SpecificationWithJUnit
import spray.http._
import spray.routing.{HttpService, Directives}
import spray.testkit.Specs2RouteTest
import scala.concurrent.Future

class SprayCommandResponseTestCommand extends BaseTestCommand {
  override def commandName: String = "SprayCommandResponseTest"
  override def path: String = "/test/SprayCommandResponse"
  val responseData = new JObject(List())

  override def execute[T](bean: Option[CommandBean]): Future[SprayCommandResponse[T]] = {
    Future (new SprayCommandResponse[T](
      Some(responseData.asInstanceOf[T]),
      status = StatusCodes.Accepted,
      additionalHeaders = List (
        HttpHeaders.RawHeader("custom", "header")
      )
    ))
  }
}

class SprayCommandResponseSpec extends SpecificationWithJUnit
  with Directives
  with Specs2RouteTest
  with HttpService {

  def actorRefFactory = system

  val testCommandRef = TestActorRef[SprayCommandResponseTestCommand]
  val testActor = testCommandRef.underlyingActor

  "SprayCommandResponse " should {

    "use specified status code and headers" in {
      Get("/test/SprayCommandResponse") ~> RouteManager.getRoute("SprayCommandResponseTest_get").get ~> check {
        status mustEqual StatusCodes.Accepted
        headers.exists( h => h.name == "custom" && h.value == "header") must beTrue
      }
    }

  }
}
