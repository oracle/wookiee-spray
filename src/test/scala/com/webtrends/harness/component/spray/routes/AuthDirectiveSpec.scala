package com.webtrends.harness.component.spray.routes

import akka.testkit.TestActorRef
import com.webtrends.harness.component.spray.route.RouteManager
import org.scalatest.{FunSuite, MustMatchers}
import spray.http.HttpHeaders.Authorization
import spray.http._
import spray.routing.{Directives, HttpService}
import spray.testkit.ScalatestRouteTest

class AuthDirectiveSpec extends FunSuite with Directives with ScalatestRouteTest with HttpService with MustMatchers {
  def actorRefFactory = system

  val testCommandRef = TestActorRef[AuthTestCommand]
  val testActor = testCommandRef.underlyingActor
  
  test("should handle Get requests using basic auth") {
    HttpRequest(
      HttpMethods.GET,
      "/foo/key1/bar/key2",
      List(Authorization(BasicHttpCredentials("good", "whatever"))),
      None
    ) ~> RouteManager.getRoute("AuthTest_get").get ~> check {
      status.intValue mustEqual 200
      header("WWW-Authenticate").isEmpty mustEqual true
    }
  }

  test("should fail Get requests using wrong basic creds") {
    HttpRequest(
      HttpMethods.GET,
      "/foo/key1/bar/key2",
      List(Authorization(BasicHttpCredentials("bad", "whatever"))),
      None
    ) ~> RouteManager.getRoute("AuthTest_get").get ~> check {
      status.intValue mustEqual 401
      header("WWW-Authenticate").get.value mustEqual "Basic realm=session"
    }
  }

  test("should fail Get requests using no creds") {
    Get("/foo/key1/bar/key2") ~> RouteManager.getRoute("AuthTest_get").get ~> check {
      status.intValue mustEqual 401
      header("WWW-Authenticate").get.value mustEqual "Basic realm=session"
    }
  }

  test("should handle Get requests using token auth") {
    HttpRequest(
      HttpMethods.GET,
      "/foo/key1/bar/key2",
      List(Authorization(OAuth2BearerToken("good"))),
      None
    ) ~> RouteManager.getRoute("AuthTest_get").get ~> check {
      status.intValue mustEqual 200
      header("WWW-Authenticate").isEmpty mustEqual true
    }
  }

  test("should fail Get requests using wrong token creds") {
    HttpRequest(
      HttpMethods.GET,
      "/foo/key1/bar/key2",
      List(Authorization(OAuth2BearerToken("bad"))),
      None
    ) ~> RouteManager.getRoute("AuthTest_get").get ~> check {
      status.intValue mustEqual 401
      header("WWW-Authenticate").get.value mustEqual "Basic realm=session"
    }
  }
}
