package com.webtrends.harness.component.spray.routes

import java.util.concurrent.TimeUnit

import akka.actor.Props
import com.webtrends.harness.command.{BaseCommandResponse, Command, CommandBean, CommandResponse}
import com.webtrends.harness.component.spray.route._
import org.scalatest.{FunSuite, MustMatchers}
import spray.http._
import spray.routing.{Directives, HttpService}
import spray.testkit.ScalatestRouteTest

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

case class Response(bytesAsString: String, contentType: String)

class ByteEntityTestCommand() extends Command with SprayPostBytes with SprayPutBytes {
  implicit val executionContext = context.dispatcher
  override def commandName = "ByteEntityTest"
  override def path = s"/$commandName"

  override def execute[T:Manifest](bean: Option[CommandBean]): Future[BaseCommandResponse[T]] = Future {
    val dataAsString = new String(bean.get.getValue[Array[Byte]](CommandBean.KeyEntity).get, "utf-8")
    val contentType = bean.get.getValue[Option[String]](SprayRoutes.KeyEntityType).get.getOrElse("")
    CommandResponse(Some(Response(dataAsString, contentType).asInstanceOf[T]))
  }

}

class ByteEntitySpec extends FunSuite
  with Directives
  with ScalatestRouteTest
  with HttpService
  with MustMatchers {

  implicit def default = RouteTestTimeout(FiniteDuration(60, TimeUnit.SECONDS))
  def actorRefFactory = system

  val testCommandRef = system.actorOf(Props(new ByteEntityTestCommand()))

  test("Add Content-Type and raw byte array to Command Bean") {

    HttpRequest(
      HttpMethods.POST,
      "/ByteEntityTest",
      List(HttpHeaders.`Content-Type`(ContentTypes.`application/json`)),
      Some(HttpEntity("TestString".getBytes("utf-8")))
    ) ~>
      RouteManager.getRoute("ByteEntityTest_post").get ~> check {
      responseAs[String] == """{"bytesAsString":"TestString","contentType":"application/json; charset=UTF-8"}""" && handled mustBe true
    }
  }

  test("not require a content-type") {

    HttpRequest(
      HttpMethods.POST,
      "/ByteEntityTest",
      List.empty[HttpHeader],
      Some(HttpEntity("TestString".getBytes("utf-8")))
    ) ~>
      RouteManager.getRoute("ByteEntityTest_post").get ~> check {
      responseAs[String] == """{"bytesAsString":"TestString","contentType":""}""" && handled mustBe true
    }
  }

  test("Add Content-Type and raw byte array to Command Bean") {

    HttpRequest(
      HttpMethods.PUT,
      "/ByteEntityTest",
      List(HttpHeaders.`Content-Type`(ContentTypes.`application/json`)),
      Some(HttpEntity("TestString".getBytes("utf-8")))
    ) ~>
      RouteManager.getRoute("ByteEntityTest_put").get ~> check {
      responseAs[String] == """{"bytesAsString":"TestString","contentType":"application/json; charset=UTF-8"}""" && handled mustBe true
    }
  }

  test("not require a content-type") {

    HttpRequest(
      HttpMethods.PUT,
      "/ByteEntityTest",
      List.empty[HttpHeader],
      Some(HttpEntity("TestString".getBytes("utf-8")))
    ) ~>
      RouteManager.getRoute("ByteEntityTest_put").get ~> check {
      responseAs[String] == """{"bytesAsString":"TestString","contentType":""}""" && handled mustBe true
    }
  }
}


