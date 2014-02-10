package com.traversalsoftware.runway.test

import com.traversalsoftware._
import org.specs2.mutable._

import play.api.libs.json._

import scala.concurrent.duration._
import scala.concurrent._

import play.modules.reactivemongo.json.collection.JSONCollection
import play.modules.reactivemongo._

import org.specs2.specification.AllExpectations
import org.specs2.mock._

import scala.language.reflectiveCalls

class RunwayModelSpec extends Specification with Mockito {

  import ExecutionContext.Implicits.global

  def resolve[A](a: Future[A]) = {
    Await.result(a, scala.concurrent.duration.DurationInt(5) seconds)
  }

  // object FakeModel extends runway.RunwayModelCompanion[FakeModel] {

  //   def getModel() = new FakeModel("")

  implicit val fakeModelWrites = new Writes[FakeModel] {
    def writes(p: FakeModel): JsValue = {
      Json.obj()
    }
  }

  implicit val fakeModelReads = new Reads[FakeModel] {
    def reads(p: JsValue): JsResult[FakeModel] = {
      new JsSuccess(new FakeModel(""))
    }
  }
  // }

  class FakeModel(val value1: String, val mockedTool: runway.Stylist[FakeModel] = null) extends runway.RunwayModel[FakeModel] {
    override val tool = mockedTool

    val id = "an Id"

    def jsonReads(t: JsValue) = {
      val v = "value1"
      new FakeModel(v)
    }
    def jsonWrites() = {
      Json.obj("value1" -> value1)
    }
  }

  var testModel: FakeModel = null

  "An RunwayModel should" should {
    "be able to be instantiated" in {
      testModel = new FakeModel("some string")

      testModel must not beNull
    }
    "pass along find to the model tool" in {
      val mockedTool = mock[runway.Stylist[FakeModel]]
      var t = new FakeModel("some string", mockedTool)

      mockedTool.find("an Id") returns future {
        Some(t)
      }

      resolve[Option[FakeModel]](t.find("an Id")) should beSome(t)

      there was one(mockedTool).find("an Id")
    }
    "findOrFail should find extant models" in {
      val mockedTool = mock[runway.Stylist[FakeModel]]
      var t = new FakeModel("some string", mockedTool)

      mockedTool.find("an Id") returns future {
        Some(t)
      }

      resolve[Option[FakeModel]](t.findOrFail("an Id")) should beSome(t)

      there was one(mockedTool).find("an Id")
    }
    "and throw an exception on non-extanistant models" in {
      val mockedTool = mock[runway.Stylist[FakeModel]]
      var t = new FakeModel("some string", mockedTool)

      mockedTool.find("an Id") returns future {
        None
      }

      resolve[Option[FakeModel]](t.findOrFail("an Id")) must throwA[runway.ModelNotFoundException]

      there was one(mockedTool).find("an Id")
    }
    "pass along finding multiple to the model tool" in {
      val mockedTool = mock[runway.Stylist[FakeModel]]
      var t = new FakeModel("some string", mockedTool)

      mockedTool.find(List("an Id")) returns future {
        List(t)
      }

      resolve[List[FakeModel]](t.find(List("an Id")))(0) should_== t

      there was one(mockedTool).find(List("an Id"))
    }
    "pass along all to the model tool" in {
      val mockedTool = mock[runway.Stylist[FakeModel]]
      var t = new FakeModel("some string", mockedTool)

      mockedTool.all returns future {
        List(t)
      }

      resolve[List[FakeModel]](t.all)(0) should_== t

      there was one(mockedTool).all
    }
  }

}
