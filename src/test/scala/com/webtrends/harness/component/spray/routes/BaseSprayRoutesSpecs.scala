/*
 * Copyright 2015 Webtrends (http://www.webtrends.com)
 *
 * See the LICENCE.txt file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.webtrends.harness.component.spray.routes

import akka.testkit.TestActorRef
import com.webtrends.harness.component.spray.route.RouteManager
import org.scalatest.{FunSuite, MustMatchers}
import spray.routing.{Directives, HttpService}
import spray.testkit.ScalatestRouteTest

/**
 * @author Michael Cuthbert on 12/19/14.
 */
class BaseSprayRoutesSpecs extends FunSuite with Directives with ScalatestRouteTest with HttpService with MustMatchers {
  def actorRefFactory = system

  val testCommandRef = TestActorRef[BaseTestCommand]
  val testActor = testCommandRef.underlyingActor
  val testCustomCommandRef = TestActorRef[CustomTestCommand]
  val testCustomActor = testCustomCommandRef.underlyingActor
  
  test("should handle Get requests using SprayGet") {
    Get("/foo/key1/bar/key2") ~> RouteManager.getRoute("BaseTest_get").get ~> check {
      handled mustBe true
    }
  }

  test("should not handle Post requests using SprayGet") {
    Post("/foo/key1/bar/key2") ~> RouteManager.getRoute("BaseTest_get").get ~> check {
      handled mustBe false
    }
  }

  test("should handle Get requests with different keys using SprayGet") {
    Get("/foo/1234/bar/5678") ~> RouteManager.getRoute("BaseTest_get").get ~> check {
      handled mustBe true
    }
  }

  test("should handle custom requests from Command using SprayCustom") {
    Get("/foo/bar") ~> RouteManager.getRoute("CustomTest_custom").get ~> check {
      handled mustBe true
    }
  }

  test("should handle Head requests using SprayHead") {
    Head("/foo/key1/bar/kye2") ~> RouteManager.getRoute("BaseTest_head").get ~> check {
      handled mustBe true
    }
  }

  test("should handle Options requests using SprayOption") {
    Options("/foo/key1/bar/key2") ~> RouteManager.getRoute("BaseTest_options").get ~> check {
      handled mustBe true
    }
  }

  test("should handle Patch requests using SprayPatch") {
    Patch("/foo/key1/bar/key2") ~> RouteManager.getRoute("BaseTest_patch").get ~> check {
      handled mustBe true
    }
  }
}
