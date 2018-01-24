package com.webtrends.harness.component.spray.directive

import akka.testkit.TestActorRef
import com.webtrends.harness.command.{Command, CommandBean}
import com.webtrends.harness.component.spray.command.SprayCommandResponse
import com.webtrends.harness.component.spray.route.{RouteManager, SprayGet}
import org.json4s.JObject
import org.scalatest.{FunSuite, MustMatchers}
import spray.http.HttpHeaders.{Origin, RawHeader, `Access-Control-Request-Headers`}
import spray.http._
import spray.routing.{Directives, HttpService}
import spray.testkit.ScalatestRouteTest

import scala.concurrent.Future

class CORSDefault extends Command with SprayGet with CORS {
  import context.dispatcher
  override def commandName: String = "CORSDefault"
  override def path: String = "/test/CORSDefault"
  val responseData = new JObject(List())

  override def execute[T:Manifest](bean: Option[CommandBean]): Future[SprayCommandResponse[T]] = {
    Future (new SprayCommandResponse[T](Some(responseData.asInstanceOf[T])))
  }
}

class CORSRejectNoOrigin extends Command with SprayGet with CORS {
  import context.dispatcher
  override def commandName: String = "CORSRejectNoOrigin"
  override def path: String = "/test/CORSRejectNoOrigin"

  override def corsRequired = true

  val responseData = new JObject(List())

  override def execute[T:Manifest](bean: Option[CommandBean]): Future[SprayCommandResponse[T]] = {
    Future (new SprayCommandResponse[T](Some(responseData.asInstanceOf[T])))
  }
}

class CORSLimitedAllowedOrigins extends Command with SprayGet with CORS {
  import context.dispatcher
  override def commandName: String = "CORSLimitedAllowedOrigins"
  override def path: String = "/test/CORSLimitedAllowedOrigins"

  override def corsAllowedOrigins(requestOrigin: HttpOrigin): AllowedOrigins = SomeOrigins(
    Seq(
      HttpOrigin("http://www.a.com"),
      HttpOrigin("http://www.b.com"),
      HttpOrigin("http://www.c.com")
    )
  )

  val responseData = new JObject(List())

  override def execute[T:Manifest](bean: Option[CommandBean]): Future[SprayCommandResponse[T]] = {
    Future (new SprayCommandResponse[T](Some(responseData.asInstanceOf[T])))
  }
}

class CORSCustomResponseHeaders extends Command with SprayGet with CORS {
  import context.dispatcher
  override def commandName: String = "CORSCustomResponseHeaders"
  override def path: String = "/test/CORSCustomResponseHeaders"
  val responseData = new JObject(List())

  override def getResponseHeaders = Map(
    "get" -> List(
      RawHeader("X-Custom2", "v2")
    )
  )

  override def execute[T:Manifest](bean: Option[CommandBean]): Future[SprayCommandResponse[T]] = {
    Future (new SprayCommandResponse[T](
      data = Some(responseData.asInstanceOf[T]),
      additionalHeaders = List(
        RawHeader("X-Custom1", "v1")
      )
    ))
  }
}

class CORSException extends Command with SprayGet with CORS {
  override def commandName: String = "CORSException"
  override def path: String = "/test/CORSException"
  val responseData = new JObject(List())

  override def execute[T:Manifest](bean: Option[CommandBean]): Future[SprayCommandResponse[T]] = {
    throw new Exception("command failed")
  }
}

class CORSSpec extends FunSuite
  with Directives
  with ScalatestRouteTest
  with HttpService
  with MustMatchers {

  def actorRefFactory = system

  val default = TestActorRef[CORSDefault]
  val rejectNoOriginCommand = TestActorRef[CORSRejectNoOrigin]
  val limitedAllowedOrigins = TestActorRef[CORSLimitedAllowedOrigins]
  val customResponseHeaders = TestActorRef[CORSCustomResponseHeaders]
  val exception = TestActorRef[CORSException]


  test("Allow request to pass through but do not insert CORS response headers when Origin request header is missing") {
    Options("/test/CORSDefault") ~> RouteManager.getRoute("CORSDefault_options").get ~> check {
      status mustEqual StatusCodes.OK

      // This is inserted by wookiee-spray route management, not the CORS Directive
      headers.find(_.name == "Access-Control-Allow-Methods").get.value mustEqual "GET, OPTIONS"

      headers.exists(_.name == "Access-Control-Allow-Origin") mustBe false
      headers.exists(_.name == "Access-Control-Allow-Credentials") mustBe false
      headers.exists(_.name == "Access-Control-Max-Age") mustBe false
      headers.exists(_.name == "Access-Control-Allow-Headers") mustBe false
      headers.exists(_.name == "Access-Control-Expose-Headers") mustBe false
    }
  }

  test("Include default response headers when only Origin request header is present") {

    HttpRequest(
      HttpMethods.OPTIONS,
      "/test/CORSDefault",
      List(Origin(Seq(HttpOrigin("http://www.foo.test")))),
      None
    ) ~> RouteManager.getRoute("CORSDefault_options").get ~> check {
      status mustEqual StatusCodes.OK
      headers.find(_.name == "Access-Control-Allow-Methods").get.value mustEqual "GET, OPTIONS"
      headers.find(_.name == "Access-Control-Allow-Origin").get.value mustEqual "http://www.foo.test"
      headers.find(_.name == "Access-Control-Allow-Credentials").get.value mustEqual "true"
      headers.find(_.name == "Access-Control-Max-Age").get.value mustEqual String.valueOf(Long.MaxValue)
      headers.exists(_.name == "Access-Control-Allow-Headers") mustBe false
      headers.exists(_.name == "Access-Control-Expose-Headers") mustBe false
    }
  }

  test("Echo back Access-Control-Request-Headers in Access-Control-Allow-Headers") {

    HttpRequest(
      HttpMethods.OPTIONS,
      "/test/CORSDefault",
      List(
        Origin(Seq(HttpOrigin("http://www.foo.test"))),
        `Access-Control-Request-Headers`(Seq("A", "B", "C"))
      ),
      None
    ) ~> RouteManager.getRoute("CORSDefault_options").get ~> check {
      status mustEqual StatusCodes.OK
      headers.find(_.name == "Access-Control-Allow-Headers").get.value mustEqual "A, B, C"
    }
  }

  test("Reject requests with no Origin if non CORS request are disabled") {
    Options("/test/CORSRejectNoOrigin") ~> RouteManager.getRoute("CORSRejectNoOrigin_options").get ~> check {
      status mustEqual StatusCodes.Unauthorized
    }
  }

  test("Allow request to pass through but do not insert CORS response headers when Origin request header is missing, second") {
    Get("/test/CORSDefault") ~> RouteManager.getRoute("CORSDefault_get").get ~> check {
      status mustEqual StatusCodes.OK

      headers.exists(_.name == "Access-Control-Allow-Methods") mustBe false
      headers.exists(_.name == "Access-Control-Allow-Origin") mustBe false
      headers.exists(_.name == "Access-Control-Allow-Credentials") mustBe false
      headers.exists(_.name == "Access-Control-Max-Age") mustBe false
      headers.exists(_.name == "Access-Control-Allow-Headers") mustBe false
      headers.exists(_.name == "Access-Control-Expose-Headers") mustBe false
    }
  }

  test("Include default response headers when Origin request header is present") {
    HttpRequest(
      HttpMethods.GET,
      "/test/CORSDefault",
      List(Origin(Seq(HttpOrigin("http://www.foo.test")))),
      None
    ) ~> RouteManager.getRoute("CORSDefault_get").get ~> check {
      status mustEqual StatusCodes.OK
      headers.find(_.name == "Access-Control-Allow-Origin").get.value mustEqual "http://www.foo.test"
      headers.find(_.name == "Access-Control-Allow-Credentials").get.value mustEqual "true"
      headers.exists(_.name == "Access-Control-Max-Age") mustBe false
      headers.exists(_.name == "Access-Control-Allow-Headers") mustBe false
      headers.exists(_.name == "Access-Control-Expose-Headers") mustBe false
    }
  }

  test("Add Access-Control-Expose-Headers ") {

    HttpRequest(
      HttpMethods.GET,
      "/test/CORSCustomResponseHeaders",
      List(Origin(Seq(HttpOrigin("http://www.foo.test")))),
      None
    ) ~> RouteManager.getRoute("CORSCustomResponseHeaders_get").get ~> check {
      status mustEqual StatusCodes.OK
      headers.find(_.name == "Access-Control-Allow-Origin").get.value mustEqual "http://www.foo.test"
      headers.find(_.name == "Access-Control-Allow-Credentials").get.value mustEqual "true"
      headers.exists(_.name == "Access-Control-Max-Age") mustBe false
      headers.exists(_.name == "Access-Control-Allow-Headers") mustBe false

      headers.exists(_.name == "X-Custom1") mustBe true
      headers.exists(_.name == "X-Custom2") mustBe true
      headers.find(_.name == "Access-Control-Expose-Headers").get.value mustEqual "X-Custom1, X-Custom2"
    }
  }


  test("Reject requests with no Origin if non CORS request are disabled, second") {
    Get("/test/CORSRejectNoOrigin") ~> RouteManager.getRoute("CORSRejectNoOrigin_get").get ~> check {
      status mustEqual StatusCodes.Unauthorized
    }
  }

  test("Reject requests with non matching origin") {
    HttpRequest(
      HttpMethods.GET,
      "/test/CORSLimitedAllowedOrigins",
      List(Origin(Seq(HttpOrigin("http://www.d.com")))),
      None
    ) ~> RouteManager.getRoute("CORSLimitedAllowedOrigins_get").get ~> check {
      status mustEqual StatusCodes.Unauthorized
    }
  }


  test("Include default response headers when Origin request header is present and command threw an exception") {

    HttpRequest(
      HttpMethods.GET,
      "/test/CORSException",
      List(Origin(Seq(HttpOrigin("http://www.foo.test")))),
      None
    ) ~> RouteManager.getRoute("CORSException_get").get ~> check {
      status mustEqual StatusCodes.InternalServerError
      headers.find(_.name == "Access-Control-Allow-Origin").get.value mustEqual "http://www.foo.test"
      headers.find(_.name == "Access-Control-Allow-Credentials").get.value mustEqual "true"
      headers.exists(_.name == "Access-Control-Max-Age") mustBe false
      headers.exists(_.name == "Access-Control-Allow-Headers") mustBe false
      headers.exists(_.name == "Access-Control-Expose-Headers") mustBe false
    }
  }
}
