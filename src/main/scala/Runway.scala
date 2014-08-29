package com.traversalsoftware.runway

// Reactive Mongo imports
import reactivemongo.api._

// Reactive Mongo plugin
import play.modules.reactivemongo._
import play.modules.reactivemongo.json.collection.JSONCollection

// Play Json imports
import play.api.libs.json._

import play.api.Play.current

import scala.concurrent.{ Future, ExecutionContext }
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext.Implicits.global

import scala.language.reflectiveCalls

import scala.reflect.runtime.universe._

sealed abstract class Direction
case class Foward() extends Direction
case class Backward() extends Direction

class ManyToMany[A <: RunwayModel[A], B <: RunwayModel[B]](pivot: String, o: A, dummy: B)(implicit readsA: Reads[A], writesA: Writes[A], readsB: Reads[B], writesB: Writes[B]) {

  var relationshipEvents: Map[String, (A, B) ⇒ Unit] = Map empty

  def on(trigger: String, action: ((A, B) ⇒ Unit)) = {
    relationshipEvents = relationshipEvents.updated(trigger, action);
  }

  def getAll(): Future[List[(String, String)]] = {
    def collection: JSONCollection = ReactiveMongoPlugin.db.collection[JSONCollection](pivot)

    val cursor: Cursor[JsObject] = collection.
      find(Json.obj()).
      cursor[JsObject]

    val related: Future[List[JsObject]] = cursor.collect[List]()

    related.map(fut ⇒ {
      fut.map(a ⇒ ((a \ "from").as[String] -> (a \ "to").as[String])).toList
    })
  }

  def getRelated(): Future[List[B]] = {
    def collection: JSONCollection = ReactiveMongoPlugin.db.collection[JSONCollection](pivot)

    val cursor: Cursor[JsObject] = collection.
      find(Json.obj("from" -> Json.toJson(o().id))).
      cursor[JsObject]

    val related: Future[List[JsObject]] = cursor.collect[List]()

    related.flatMap(fut ⇒ {
      dummy.find(fut.map(a ⇒ (a \ "to").as[String]))
    })
  }

  def getRelatedReverse(): Future[List[B]] = {
    def collection: JSONCollection = ReactiveMongoPlugin.db.collection[JSONCollection](pivot)

    val cursor: Cursor[JsObject] = collection.
      find(Json.obj("to" -> Json.toJson(o().id))).
      cursor[JsObject]

    val related: Future[List[JsObject]] = cursor.collect[List]()

    related.flatMap(fut ⇒ {
      dummy.find(fut.map(a ⇒ (a \ "from").as[String]))
    })
  }

  def isRelatedTo(target: String): Future[Boolean] = {
    def collection: JSONCollection = ReactiveMongoPlugin.db.collection[JSONCollection](pivot)

    val cursor: Cursor[JsObject] = collection.
      find(Json.obj("from" -> Json.toJson(o().id), "to" -> Json.toJson(target))).
      cursor[JsObject]

    val related: Future[List[JsObject]] = cursor.collect[List]()

    related.map(fut ⇒ {
      (fut.length > 0)
    })
  }

  def attach(toAttach: B) = {
    relationshipEvents.get("attach") match {
      case Some(event) ⇒ event(o, toAttach)
      case None        ⇒ {}
    }
    def collection: JSONCollection = ReactiveMongoPlugin.db.collection[JSONCollection](pivot)
    val json = Json.obj("from" -> o().id, "to" -> toAttach().id)
    collection.save(json)
  }

  def attach(toAttachId: String) = {
    relationshipEvents.get("attach") match {
      case Some(event) ⇒ {
        dummy.find(toAttachId) onSuccess {
          case Some(a) ⇒ event(o, a)
          case None    ⇒ {}
        }
      }
      case None ⇒ {}
    }
    def collection: JSONCollection = ReactiveMongoPlugin.db.collection[JSONCollection](pivot)
    val json = Json.obj("from" -> o().id, "to" -> toAttachId)
    collection.save(json)
  }

  def detach(toDetach: B) = {
    def collection: JSONCollection = ReactiveMongoPlugin.db.collection[JSONCollection](pivot)
    val json = Json.obj("from" -> o().id, "to" -> toDetach().id)
    collection.remove(json)
  }

  def detach(toDetachId: String) = {
    def collection: JSONCollection = ReactiveMongoPlugin.db.collection[JSONCollection](pivot)
    val json = Json.obj("from" -> o().id, "to" -> toDetachId)
    collection.remove(json)
  }

  def apply(direction: Direction = Foward()) = {
    direction match {
      case Foward()   ⇒ getRelated()
      case Backward() ⇒ getRelatedReverse()
    }
  }

  def clean = {
    def collection: JSONCollection = ReactiveMongoPlugin.db.collection[JSONCollection](pivot)
    val json = Json.obj("from" -> o().id)
    collection.remove(json)
  }

}

class ManyToEither[A <: RunwayModel[A], B <: RunwayModel[B], C <: RunwayModel[C]](pivot: String, o: A, dummy1: B, dummy2: C)(implicit readsA: Reads[A], writesA: Writes[A], readsB: Reads[B], writesB: Writes[B], readsC: Reads[C], writesC: Writes[C]) {

  def attach(toAttach: Either[B, C]) = {
    val toAttachId = toAttach match {
      case Right(a) ⇒ Json.obj("Right" -> a().id)
      case Left(a)  ⇒ Json.obj("Left" -> a().id)
    }

    def collection: JSONCollection = ReactiveMongoPlugin.db.collection[JSONCollection](pivot)
    val json = Json.obj("from" -> o().id, "to" -> toAttachId)
    collection.save(json)

  }

  def getRelated(): Future[List[Either[B, C]]] = {
    def collection: JSONCollection = ReactiveMongoPlugin.db.collection[JSONCollection](pivot)

    val cursor: Cursor[JsObject] = collection.
      find(Json.obj("from" -> Json.toJson(o().id))).
      cursor[JsObject]

    val related: Future[List[JsObject]] = cursor.collect[List]()

    related.flatMap(fut ⇒ {
      Future.sequence(fut.map(a ⇒ {
        val keys = ((a \ "to").as[JsObject]).keys
        keys.head match {
          case "Right" ⇒ dummy2.find((a \ "to" \ "Right").as[String]).map(res ⇒ Right(res.getOrElse(dummy2)))
          case _       ⇒ dummy1.find((a \ "to" \ "Left").as[String]).map(res ⇒ Left(res.getOrElse(dummy1)))
        }
      }))
    })
  }

  def clean = {
    def collection: JSONCollection = ReactiveMongoPlugin.db.collection[JSONCollection](pivot)
    val json = Json.obj("from" -> o().id)
    collection.remove(json)
  }

}

class ModelNotFoundException(id: String) extends RuntimeException(id)

trait Jsonable[T] extends Runnable[T] { self: T ⇒
  def jsonReads(p: JsValue): T
  def jsonWrites: JsValue

  val id: String
}

trait Runnable[T] {
  val tool: Stylist[T]

  def all = tool.all

  def allQuery = tool.allQuery

  def find(id: String)(implicit reads: Reads[T], writes: Writes[T]) = {
    tool.find(id: String)
  }

  def findOrFail(id: String): Future[Option[T]] = {
    tool.find(id: String).map(f ⇒ {
      f match {
        case Some(a) ⇒ Some(a)
        case None    ⇒ throw new ModelNotFoundException(id)
      }
    })
  }

  def find(ids: List[String])(implicit reads: Reads[T], writes: Writes[T]) = tool.find(ids: List[String])

  def where(field: String, value: JsValue) = tool.where(field: String, value: JsValue)
}

class Events[T](var events: Map[String, (RunwayModel[T] with T) ⇒ Unit]) {

}

trait RunwayModel[T] extends Jsonable[T] { self: T ⇒

  def getModel = self

  val tool = new Stylist[T](getModel)

  val elegantEvents: Events[T] = new Events(Map empty)

  def on(trigger: String, action: (RunwayModel[T] with T) ⇒ Unit) = {
    elegantEvents.events = elegantEvents.events.updated(trigger, action);
  }

  def apply() = self.getModel

  def save() {
    elegantEvents.events.get("save") match {
      case Some(event) ⇒ event(getModel)
      case None        ⇒ {}
    }
    tool.save(getModel.id)
  }
}

trait RunwayModelCompanion[T] extends Runnable[T] { self: { def getModel: T with RunwayModel[T] } ⇒

  lazy implicit val instance = this.getModel

  val tool = new Stylist[T](self.getModel, getSlug)

  def getSlug = {
    val temp = self.getClass.getSimpleName
    if (temp.last == '$') {
      temp.dropRight(1).toLowerCase + "s"
    } else {
      temp.toLowerCase + "s"
    }
  }

  def save(toSave: T with RunwayModel[T]) {
    val tool = new Stylist[T](toSave)
    tool.save(toSave.id)
  }

}