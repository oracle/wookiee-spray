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

import com.typesafe.config.{Config, ConfigFactory}
import com.webtrends.harness.authentication.CIDRRules
import org.scalatest.FunSuite
import spray.http.HttpHeaders._
import spray.http.StatusCodes
import spray.testkit.ScalatestRouteTest

class CIDRNoIPSpec extends FunSuite with ScalatestRouteTest with CIDRDirectives {

  implicit var cidrRules:Option[CIDRRules] = Some(CIDRRules(CIDRConfig.allowConf))

  test("reject when there is no source ip") {
    Get("/good") ~> {
      cidrFilter {
        complete("good")
      }
    } ~> check {
      status === StatusCodes.NotFound
    }
  }
}


class CIDRAllowSpec extends FunSuite with ScalatestRouteTest with CIDRDirectives {

  implicit var cidrRules:Option[CIDRRules] = Some(CIDRRules(CIDRConfig.allowConf))

  test("accept a defined address using 'allow'") {
    Get("/good") ~> addHeader(`Remote-Address`("127.0.0.1")) ~> {
      cidrFilter {
        complete("good")
      }
    } ~> check {
      status === StatusCodes.OK
    }
  }

  test("accept a defined second address using 'allow'") {
    implicit var settings:Option[CIDRRules] = Some(CIDRRules(CIDRConfig.allowConf))
    Get("/good") ~> addHeader(`Remote-Address`("10.88.16.32")) ~> {
      cidrFilter {
        complete("good")
      }
    } ~> check {
      status === StatusCodes.OK
    }
  }

  test("reject an un-defined address using 'allow'") {
    implicit var settings:Option[CIDRRules] = Some(CIDRRules(CIDRConfig.allowConf))
    Get("/bad") ~> addHeader(`Remote-Address`("216.64.169.240")) ~> {
      cidrFilter {
        complete("bad")
      }
    } ~> check {
      status === StatusCodes.NotFound
    }
  }
}


class CIDRDenySpec extends FunSuite with ScalatestRouteTest with CIDRDirectives {
  implicit var cidrRules:Option[CIDRRules] = Some(CIDRRules(CIDRConfig.denyConf))

  test("accept a defined address using 'deny'") {
    implicit var settings:Option[CIDRRules] = Some(CIDRRules(CIDRConfig.denyConf))
    Get("/good") ~> addHeader(`Remote-Address`("127.0.0.1")) ~> {
      cidrFilter {
        complete("good")
      }
    } ~> check {
      status === StatusCodes.OK
    }
  }

  test("reject a defined address using 'deny'") {
    implicit var settings:Option[CIDRRules] = Some(CIDRRules(CIDRConfig.denyConf))
    Get("/bad") ~> addHeader(`Remote-Address`("10.88.16.32")) ~> {
      cidrFilter {
        complete("bad")
      }
    } ~> check {
      status === StatusCodes.NotFound
    }
  }
}


class CIDRMixSpec extends FunSuite with ScalatestRouteTest with CIDRDirectives {
  implicit var cidrRules:Option[CIDRRules] = Some(CIDRRules(CIDRConfig.mixConf))

  test("accept a defined address using 'mix'") {
    implicit var settings:Option[CIDRRules] = Some(CIDRRules(CIDRConfig.mixConf))
    Get("/good") ~> addHeader(`Remote-Address`("127.0.0.1")) ~> {
      cidrFilter {
        complete("good")
      }
    } ~> check {
      status === StatusCodes.OK
    }
  }

  test("reject an un-defined address using 'mix'") {
    implicit var cidrRules:Option[CIDRRules] = Some(CIDRRules(CIDRConfig.mixConf))
    Get("/good") ~> addHeader(`Remote-Address`("216.64.169.240")) ~> {
      cidrFilter {
        complete("bad")
      }
    } ~> check {
      status === StatusCodes.NotFound
    }
  }

  test("reject a defined second address using 'mix'") {
    implicit var settings:Option[CIDRRules] = Some(CIDRRules(CIDRConfig.mixConf))
    Get("/bad") ~> addHeader(`Remote-Address`("10.88.16.32")) ~> {
      cidrFilter {
        complete("bad")
      }
    } ~> check {
      status === StatusCodes.NotFound
    }
  }
}

object CIDRConfig {
  val allowConf: Config = ConfigFactory.parseString( """
        cidr-rules {
          allow=["127.0.0.1/30", "10.0.0.0/8"]
          deny=[]
        }
    """)

  val denyConf: Config = ConfigFactory.parseString( """
        cidr-rules {
          allow=[]
          deny=["10.0.0.0/8"]
        }
    """)

  val mixConf: Config = ConfigFactory.parseString( """
      cidr-rules {
        allow=["127.0.0.1/30"]
        deny=["10.0.0.0/8"]
      }
    """)
}
