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
import org.scalatest.FunSuite
import spray.http.{ContentTypes, HttpEntity}
import spray.routing.{Directives, HttpService}
import spray.testkit.ScalatestRouteTest

/**
 * @author Michael Cuthbert on 12/19/14.
 */
class EntitySprayRoutesSpecs extends FunSuite with Directives with ScalatestRouteTest with HttpService {
  def actorRefFactory = system

  val postCommandRef = TestActorRef[EntityTestCommand]
  val postActor = postCommandRef.underlyingActor
  val putCommandRef = TestActorRef[MarshallTestCommand]
  val putActor = putCommandRef.underlyingActor
  val customCommandRef = TestActorRef[CustomTestCommand]
  val customActor = customCommandRef.underlyingActor

  test("post requests should marshall entities correctly") {
    Post("/foo/key1/bar/key2", HttpEntity(ContentTypes.`application/json`, """{"stringKey":"string","intKey":1234}""")) ~> RouteManager.getRoute("EntityTest_post").get ~> check {
      assert(responseAs[String] == """{"stringKey":"string","intKey":1234}""" && handled)
    }
  }

  test("handle custom route correctly") {
    Get("/foo/bar") ~> RouteManager.getRoute("CustomTest_custom").get ~> check {
      assert(handled)
    }
  }
}
