package com.webtrends.harness.component.spray.http

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.io.IO
import akka.pattern.ask
import akka.testkit.TestActorRef
import akka.util.Timeout
import com.webtrends.harness.command.{CommandBean, Command}
import com.webtrends.harness.component.spray.command.SprayCommandResponse
import com.webtrends.harness.component.spray.route.{RouteManager, SprayGet}
import com.webtrends.harness.component.spray.{SprayManager, SprayTestConfig, TestKitSpecificationWithJUnit}
import com.webtrends.harness.service.test.TestHarness
import net.liftweb.json.JObject
import spray.can.Http
import spray.http.HttpMethods._
import spray.http._
import spray.testkit.Specs2RouteTest

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class InternalTestCommand extends Command with SprayGet {
  import context.dispatcher
  override def commandName: String = "InternalTestCommand"
  override def path: String = "/test/InternalTestCommand"
  val responseData = new JObject(List())

  override def execute[T](bean: Option[CommandBean]): Future[SprayCommandResponse[T]] = {
    Future (new SprayCommandResponse[T](Some(responseData.asInstanceOf[T])))
  }
}

class SprayManagerSpec extends TestKitSpecificationWithJUnit(ActorSystem("testInternal")) {
  implicit def anyToSuccess[T](a: T): org.specs2.execute.Result = success

  implicit val timeout = Timeout(10000, TimeUnit.MILLISECONDS)
  val sys = TestHarness(SprayTestConfig.config, None, Some(Map("wookiee-spray" -> classOf[SprayManager])))
  implicit val actorSystem = ActorSystem()
  val internalTestCommand = TestActorRef[InternalTestCommand]


  Thread.sleep(5000)
  sequential

  "http Server" should {

    "find a healthcheck on the internal port" in {
      val response: Future[HttpResponse] =
        (IO(Http) ? HttpRequest(GET, Uri("http://localhost:8080/healthcheck/full"))).mapTo[HttpResponse]
      Await.result(response, Duration("10 seconds")).status must beEqualTo(StatusCodes.OK)
    }

    "not find a healthcheck on the external port" in {
      val response: Future[HttpResponse] =
        (IO(Http) ? HttpRequest(GET, Uri("http://localhost:8082/healthcheck/full"))).mapTo[HttpResponse]
      Await.result(response, Duration("3 seconds")).status must beEqualTo(StatusCodes.NotFound)
    }
  }

  "InternalTestCommand" should {
    "respond on the internal port" in {
      val response: Future[HttpResponse] =
        (IO(Http) ? HttpRequest(GET, Uri("http://localhost:8080/test/InternalTestCommand"))).mapTo[HttpResponse]
      Await.result(response, Duration("10 seconds")).status must beEqualTo(StatusCodes.OK)


      //          "not respond on the external port" in {
      //
      //          }
    }
  }
}
