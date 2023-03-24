# Wookiee - Component: Spray (HTTP)

[![Build Status](https://travis-ci.org/Webtrends/wookiee-spray.svg?branch=master)](https://travis-ci.org/Webtrends/wookiee-spray) [![Coverage Status](https://coveralls.io/repos/Webtrends/wookiee-spray/badge.svg?branch=master&service=github)](https://coveralls.io/github/Webtrends/wookiee-spray?branch=master) [![Latest Release](https://img.shields.io/github/release/webtrends/wookiee-spray.svg)](https://github.com/Webtrends/wookiee-spray/releases) [![License](http://img.shields.io/:license-Apache%202-red.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt)

[Main Wookiee Project](https://github.com/Webtrends/wookiee)

For Configuration information see [Spray Config](docs/config.md)

The Spray component has both server and client http functionality. The server component will allow connections to the service using spray. The client will allow the user to make requests and receive responses using the Akka actor methodology as it's based. Both are started up if using as a service. If using as a library if you start up the SprayManager manually both server and client will start up.

For working example see [Wookiee - Http Example](example-http) or [Wookiee - Rest Example](example-rest)

## Config
```json
wookiee-spray {
  manager = "com.webtrends.harness.component.http.SprayManager"
  enabled = true

  # The port to run the http server on
  http-port = 8080 

  # The port to run the websocket server on
  http-port = 8081 
}
spray {
  can {
    server {
      server-header = "harness"
      request-timeout = 60s
      idle-timeout = 120s

      # Enables/disables the addition of a `Remote-Address` header
      # holding the clients (remote) IP address.
      remote-address-header = on
      # Enables/disables support for statistics collection and querying.
      stats-support = on
    }
    parsing {
      max-uri-length: 16k
    }
  }
  client {
    idle-timeout = 120 s
    request-timeout = 60 s
  }
  host-connector {
    max-connections = 10
    max-retries = 2
    pipelining = on
  }
}
akka.actor.deployment {
  /system/component/wookiee-spray/spray-server/spray-base {
    router = round-robin-pool
    nr-of-instances = 3
  }
}
```

The configuration is fairly straight forward and generally you wouldn't modify these settings too much. The Spray settings are all found in here. So if you need to modify any Spray settings you would just modify them under the Spray key as seen in the example config above.

## Spray Server
The SprayManager which is the core component for the Spray component module, extends the SprayServer with the SprayClient and SprayWebSocketServer. The SprayServer, SprayClient and SprayWebsocketServer are started as child actors for the root actor (for this component) SprayManager.

### Adding a route to the server
The primary function of having a http server is to allow requests to be made to your service and a subsequent response after some business logic is executed. The easiest way to do this would be to simply create Commands and have Wookiee add the routes automatically. However, a developer can also build the routes for more complicated usage. The routes are based on Spray so for more information on building the routes themselves, head over to [Spray.io Documentation](http://spray.io/documentation/1.2.2/). To add routes manually you can do the following:
```Scala
class MyService extends SprayService {
  override def routes:Routes = manualRoute // or simply create the route inline

  def manualRoute:Route = {
    pathPrefix(Segment / "foo" / Segment / "bar") {
      (seg1, seg2) =>
        path("request") {
          get {
            ctx => ctx.complete("Hello World!")
          }
        }
    }
  }  
}
```
As you will notice this is no different from how you would create a route in previous versions of Wookiee (Harness) except for extending from SprayService instead of Service. As in previous versions the Plugin (which has been changed to Service) class by default had http built into it. HTTP now is a separate component and used only when requested. SprayService extends from Service and brings all the base functionality of Service along with it. There is also no requirement that a service needs to have http built into it, like using SprayService. If you use commands exclusively your service does not need to extend SprayService

#### Adding routes automagically
The ability to add routes automagically is as easy as mixing in traits into your commands. See [SprayRoutes](docs/SprayRoutes.md) for more information about this.

## Features of Spray Commands
If one chose to create a route automagically (see above) by extending Command and one of the Spray[Method] classes then there are some helpful features that come along

### CORS Support
Simply add the CORSDirectives class to the list of extensions to return CORS headers

### CIDR Support
Add the CIDRDirectives class to your extensions to filter by CIDR rules

### Authorization
NOTE: If it is desired to only allow one of these two types of auth, then you must still override both commands and simply have the undesired auth method return None

#### Basic Auth
To gain basic auth functionality simply override the following method in your command

```override def basicAuth(userPass: Option[UserPass]): Future[Option[String]]```

Then write your logic to check that the user/pass is legit and if so return Some(String), if not, return None to cause Unauthorized to be returned to user

#### OAuth (Bearer Token)
To gain oauth functionality simply override the following method in your command

```override def tokenAuth(tokenScope: Option[Token]): Future[Option[String]]```

The Token class contains a scope, always 'session', and the token itself which you can write your own logic to check. If the token is legit return Some(String), if not, return None to cause Unauthorized to be returned to user


## Spray Client
See [SprayClient](docs/SprayClient.md)

## Spray WebSocket Server
The websocket server allows requests to be made to your service with a response after some business logic has been executed. This is accomplished by creating WebSocketWorkers and registering the endpoint they service with Wookiee.
A websocket worker is created by extending the ```WebSocketWorker``` trait and overriding businessLogic as follows:
```Scala
class ServerWorker extends WebSocketWorker {

  override def businessLogic = ({
    case x: BinaryFrame =>
      log.info("Server BinaryFrame Received:" + x)
      sender() ! x

    case x: TextFrame =>
      if (x.payload.length <= 10) {
        log.info("Server TextFrame Received:" + x)
        sender() ! x
      } else {
        log.info("Server Large TextFrame Received:" + x)
        sender() ! TextFrameStream(1, new ByteArrayInputStream(x.payload.toArray))
      }

    case x: HttpRequest =>
      log.info("Server HttpRequest Received")

    case x: Tcp.ConnectionClosed =>
      log.info("Server Close")
  }: Receive) orElse super.businessLogic
}
```

### Adding a WebSocket endpoint to the websocket server
Endpoints can be added to the WebSocket server by implementing the addWebSocketWorkers function in your service and mapping paths to workers.  An actor will be created for each connection made to the endpoint.  
The endpoint path will be parsed allowing users to get parameters from the URL. A URL like '/account/1/user/2/obj/3' can be created with the string '/account/$accId/user/$userId/obj/$objId'. This will then match the URL and assume that any of the path elements starting with $ are variables. The variables will be placed in the CommandBean and can be retrieved in the WebSocketWorker' businessLogic using the keys accId, userId and objId. The variables will automatically be converted to Integers if it is not a String.
```Scala
class MyService extends SprayService {
  override def addWebSocketWorkers = {
    addWebSocketWorker("test", classOf[ServerWorker])
    addWebSocketWorker("endpoint2", classOf[MyServerWorker2])
    addWebSocketWorker("account/$accId/user/$userId/obj/$objId", classOf[MyServerWorker3])
  }
}
```

## Contributing
This project is not accepting external contributions at this time. For bugs or enhancement requests, please file a GitHub issue unless it’s security related. When filing a bug remember that the better written the bug is, the more likely it is to be fixed. If you think you’ve found a security vulnerability, do not raise a GitHub issue and follow the instructions in our [security policy](./SECURITY.md).

## Security

Please consult the [security guide](./SECURITY.md) for our responsible security vulnerability disclosure process

## License
Copyright (c) 2004, 2023 Oracle and/or its affiliates.
Released under the Apache License Version 2.0
