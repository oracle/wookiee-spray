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

package com.webtrends.harness.component.spray.directive

import akka.testkit.TestKit
import com.webtrends.harness.command.CommandBean
import org.scalatest.{BeforeAndAfterAll, FunSuite}
import spray.http._
import spray.httpx.RequestBuilding
import spray.testkit.ScalatestRouteTest

/**
 * @author Michael Cuthbert on 12/12/14.
 */
class CommandDirectiveSpec extends FunSuite
    with ScalatestRouteTest
    with CommandDirectives
    with RequestBuilding with BeforeAndAfterAll {

  val pathMap = Map(
    "path1" -> "/test/path1",
    "path2" -> "/path1/test",
    "path3" -> "/varpath/$var1/$var2"
  )

  val testHeaders = Map(
    HttpMethods.GET.name -> List(HttpHeaders.`Access-Control-Allow-Methods`(HttpMethods.GET)),
    HttpMethods.POST.name -> List(HttpHeaders.`Access-Control-Allow-Methods`(HttpMethods.POST)),
    CommandDirectives.KeyAllHeaders -> List(HttpHeaders.`Cache-Control`(CacheDirectives.`no-cache`))
  )
  // for the empty list case
  val testHeadersEmpty = Map[String, List[HttpHeader]]()
  
  test("respond OK on valid path") {
    Get("/test/path") ~> {
      commandPath("/test/path/") {
        _ => complete("test")
      }
    } ~> check {
      status == StatusCodes.OK
    }
  }

  test("reject invalid path") {
    Get("/test/path") ~> {
      commandPath("/test/path2/") {
        _ => complete("test")
      }
    } ~> check {
      assert(!handled)
    }
  }

  test("respond with string SEGMENT url in bean") {
    Get("/test/seg/path") ~> {
      commandPath("/test/$key/path") {
        bean => complete(s"${bean("key")}")
      }
    } ~> check {
      status == StatusCodes.OK
      body.asString == "seg"
    }
  }

  test("respond with multiple string SEGMENT url in bean") {
    Get("/test/seg/path/seg2") ~> {
      commandPath("/test/$key1/path/$key2") {
        bean => complete(s"${bean("key1")}-${bean("key2")}")
      }
    } ~> check {
      status == StatusCodes.OK
      body.asString == "seg-seg2"
    }
  }

  test("respond with int SEGMENT url in bean") {
    Get("/test/1234/path") ~> {
      commandPath("/test/$key/path") {
        bean =>
          bean("key") match {
            case x:Integer => complete(x.toString)
            case _ => complete("NA")
          }
      }
    } ~> check {
      status == StatusCodes.OK
      body.asString == "1234"
    }
  }

  test("reject invalid path with SEGMENT") {
    Get("/test/path") ~> {
      commandPath("/test/$key/path") {
        _ => complete("test")
      }
    } ~> check {
      assert(!handled)
    }
  }

  test("respond OK with valid options path") {
    Get("/test/path1") ~> {
      commandPath("/test/path1|path2") {
        _ => complete("test")
      }
    } ~> check {
      assert(handled)
    }
  }

  test("reject invalid options path") {
    Get("/test/path1") ~> {
      commandPath("/test/path2|path3") {
        _ => complete("test")
      }
    } ~> check {
      assert(!handled)
    }
  }

  test("reject any non-matching paths") {
    Get("/test/path3") ~> {
      commandPaths(pathMap) {
        _ => complete("path3")
      }
    } ~> check {
      assert(!handled)
    }
  }

  test("accept any matching paths") {
    Get("/path1/test") ~> {
      commandPaths(pathMap) {
        bean => complete(bean(CommandBean.KeyPath).toString)
      }
    } ~> check {
      body.asString == "path2"
      assert(handled)
    }
  }

  test("do variable substitution on matching path") {
    Get("/varpath/test1/1") ~> {
      commandPaths(pathMap) {
        bean => complete(s"${bean(CommandBean.KeyPath)}-${bean("var1")}-${bean("var2")}")
      }
    } ~> check {
      body.asString == "path3-test1-1"
      assert(handled)
    }
  }

  test("Get request should respond with GET header") {
    Get("/test") ~> {
      mapHeaders(testHeaders) {
        complete("headers")
      }
    } ~> check {
      val rH = header("Access-Control-Allow-Methods") match {
        case Some(s) =>
          val headerMethods = s.asInstanceOf[HttpHeaders.`Access-Control-Allow-Methods`].methods
          headerMethods.contains(HttpMethods.GET) && !headerMethods.contains(HttpMethods.POST)
        case None => false
      }
      assert(rH)
      val rH2 = header("Cache-Control") match {
        case Some(s) => s.asInstanceOf[HttpHeaders.`Cache-Control`].directives.contains(CacheDirectives.`no-cache`)
        case None => false
      }
      assert(rH2)
    }
  }

  test("Post request should respond with Options header") {
    Post("/test") ~> {
      mapHeaders(testHeaders) {
        complete("headers")
      }
    } ~> check {
      val rH = header("Access-Control-Allow-Methods") match {
        case Some(s) =>
          val headerMethods = s.asInstanceOf[HttpHeaders.`Access-Control-Allow-Methods`].methods
          !headerMethods.contains(HttpMethods.GET) && headerMethods.contains(HttpMethods.POST)
        case None => false
      }
      assert(rH)
      val rH2 = header("Cache-Control") match {
        case Some(s) => s.asInstanceOf[HttpHeaders.`Cache-Control`].directives.contains(CacheDirectives.`no-cache`)
        case None => false
      }
      assert(rH2)
    }
  }

  test("Get request with empty header set should succeed") {
    Get("/test") ~> {
      mapHeaders(testHeadersEmpty) {
        complete("headers")
      }
    } ~> check {
      assert(handled)
    }
  }

  override protected def afterAll() = TestKit.shutdownActorSystem(system)
}
