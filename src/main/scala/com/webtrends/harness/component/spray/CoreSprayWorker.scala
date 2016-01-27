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
package com.webtrends.harness.component.spray

import _root_.spray.can.server.ServerSettings
import _root_.spray.http.{HttpRequest, HttpResponse, StatusCodes}
import _root_.spray.httpx.LiftJsonSupport
import _root_.spray.routing.directives.LogEntry
import _root_.spray.routing.{HttpServiceActor, Rejected, Route}
import akka.event.Logging
import com.webtrends.harness.authentication.CIDRRules
import com.webtrends.harness.component._
import com.webtrends.harness.component.spray.directive.CIDRDirectives
import com.webtrends.harness.component.spray.route.{RouteAccessibility, RouteManager}
import com.webtrends.harness.component.spray.serialization.EnumerationSerializer
import com.webtrends.harness.health.{ComponentState, _}
import com.webtrends.harness.logging.ActorLoggingAdapter
import net.liftweb.json.ext.JodaTimeSerializers

@SerialVersionUID(1L) case class HttpStartProcessing()
@SerialVersionUID(1L) case class HttpReloadRoutes()


class CoreSprayWorker extends HttpServiceActor
    with ActorHealth
    with ActorLoggingAdapter
    with CIDRDirectives
    with LiftJsonSupport
    with ComponentHelper {

  implicit val liftJsonFormats = net.liftweb.json.DefaultFormats + new EnumerationSerializer(ComponentState) ++ JodaTimeSerializers.all

  val spSettings = ServerSettings(context.system)
  var cidrRules: Option[CIDRRules] = Some(CIDRRules(context.system.settings.config))


  def baseRoutes: Route = {
    unmatchedPath { remainingPath =>
      complete(StatusCodes.NotFound)
    }
  }

  def receive: Receive = initializing

  /**
    * Establish our routes and other receive handlers
    */
  def initializing: Receive = health  orElse {
    case HttpStartProcessing =>
      context.become(running)
    case HttpReloadRoutes => // Do nothing
  }

  def running: Receive = health orElse runRoute(logRequestResponse(myLog _) {
    getRoutes
  }) orElse {
    case HttpReloadRoutes =>
      context.become(initializing)
      self ! HttpStartProcessing
  }

  /**
    * Fetch routes from all registered services and concatenate with our default ones that
    * are defined below.
    */
  def getRoutes: Route = {
    val serviceRoutes = RouteManager.getRoutes(RouteAccessibility.EXTERNAL).filter(r => !r.equals(Map.empty))
    (serviceRoutes ++ List(this.baseRoutes)).reduceLeft(_ ~ _)
  }

  def myLog(request: HttpRequest): Any => Option[LogEntry] = {
    case x: HttpResponse => {
      println(s"Normal: $request")
      createLogEntry(request, x.status + " " + x.toString())
    }
    case Rejected(rejections) => {
      println(s"Rejection: $request")
      createLogEntry(request, " Rejection " + rejections.toString())
    }
    case x => {
      println(s"other: $request")
      createLogEntry(request, x.toString())
    }
  }

  def createLogEntry(request: HttpRequest, text: String): Some[LogEntry] = {
    Some(LogEntry("#### Request " + request + " => " + text, Logging.DebugLevel))
  }
}
