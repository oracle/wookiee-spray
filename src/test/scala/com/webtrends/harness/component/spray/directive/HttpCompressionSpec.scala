package com.webtrends.harness.component.spray.directive

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import akka.testkit.TestActorRef
import com.webtrends.harness.command.{Command, CommandBean}
import com.webtrends.harness.component.spray.command.SprayCommandResponse
import com.webtrends.harness.component.spray.route.{RouteManager, SprayGet, SprayPost}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.JsonAST.JObject
import org.specs2.mutable.SpecificationWithJUnit
import spray.http.HttpHeaders._
import spray.http._
import spray.routing.{Directives, HttpService}
import spray.testkit.{ScalatestRouteTest, Specs2RouteTest}
import java.util.zip.{GZIPInputStream, GZIPOutputStream}

import org.scalatest.{FunSuite, MustMatchers}

import scala.concurrent.Future
import scala.io.Source

class CompressResponseCommand extends Command with SprayGet with HttpCompression{
  import context.dispatcher
  override def commandName: String = "CompressResponseCommand"
  override def path: String = "/test/CompressResponseCommand"
  val responseData = new JObject(List())

  override def execute[T:Manifest](bean: Option[CommandBean]): Future[SprayCommandResponse[T]] = {
    Future (new SprayCommandResponse[T](Some(responseData.asInstanceOf[T])))
  }
}

object DecompressRequestCommand {
  implicit val formats = DefaultFormats
  val requestContent = "{\"foo\":\"bar\"}"
  val requestJson = parse(requestContent)
}
class DecompressRequestCommand extends Command with SprayPost with HttpCompression{
  import context.dispatcher
  override def commandName: String = "DecompressRequestCommand"
  override def path: String = "/test/DecompressRequestCommand"

  override def execute[T:Manifest](bean: Option[CommandBean]): Future[SprayCommandResponse[T]] = {

    if(bean.get(CommandBean.KeyEntity).asInstanceOf[JObject].values.contains("foo")) {
      Future (new SprayCommandResponse[T](Some(DecompressRequestCommand.requestJson.asInstanceOf[T])))
    }
    else {
      throw new Exception()
    }
  }
}

class HttpCompressionSpec extends FunSuite
  with Directives
  with ScalatestRouteTest
  with HttpService with MustMatchers {

  def actorRefFactory = system

  TestActorRef[CompressResponseCommand]
  TestActorRef[DecompressRequestCommand]

  test("Compress responses if supported by client") {
    HttpRequest(
      HttpMethods.GET,
      "/test/CompressResponseCommand",
      List(
        `Accept-Encoding`(HttpEncodings.gzip)
      ),
      None
    ) ~> RouteManager.getRoute("CompressResponseCommand_get").get ~> check {
      status mustEqual StatusCodes.OK
      headers.find(_.name == "Content-Encoding").get.value mustEqual "gzip"

      val zipInputStream = new GZIPInputStream(new ByteArrayInputStream(responseAs[Array[Byte]]))
      val decompressed = Source.fromInputStream(zipInputStream).mkString
      decompressed mustEqual "{}"
    }
  }

  test("Not compress responses if not supported by client") {
    Get("/test/CompressResponseCommand") ~> RouteManager.getRoute("CompressResponseCommand_get").get ~> check {
      status mustEqual StatusCodes.OK
      responseAs[String] mustEqual "{}"
    }
  }

  test("decompress requests") {

    val byteOut = new ByteArrayOutputStream(DecompressRequestCommand.requestContent.length)
    val zipOutputStream = new GZIPOutputStream(byteOut)
    zipOutputStream.write(DecompressRequestCommand.requestContent.getBytes())
    zipOutputStream.close()

    HttpRequest(
      HttpMethods.POST,
      "/test/DecompressRequestCommand",
      List(
        `Content-Encoding`(HttpEncodings.gzip)
      ),
      HttpEntity(ContentTypes.`application/json`, byteOut.toByteArray)
    ) ~> RouteManager.getRoute("DecompressRequestCommand_post").get ~> check {
      status mustEqual StatusCodes.OK
      responseAs[String] mustEqual DecompressRequestCommand.requestContent
    }
  }

  test("Not attempt to decompress requests that aren't compressed") {
    HttpRequest(
      HttpMethods.POST,
      "/test/DecompressRequestCommand",
      List(),
      HttpEntity(ContentTypes.`application/json`, DecompressRequestCommand.requestContent)
    ) ~> RouteManager.getRoute("DecompressRequestCommand_post").get ~> check {
      status mustEqual StatusCodes.OK
      responseAs[String] mustEqual DecompressRequestCommand.requestContent
    }
  }
}
