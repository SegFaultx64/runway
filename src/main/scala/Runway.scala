package com.traversalsoftware.runway

// Reactive Mongo imports
import reactivemongo.api._

// Reactive Mongo plugin
import play.modules.reactivemongo._
import play.modules.reactivemongo.json.collection.JSONCollection

// Play Json imports
import play.api.libs.json._

import play.api.Play.current

import scala.concurrent.{ Future, ExecutionContext}
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext.Implicits.global

import scala.language.reflectiveCalls

import scala.reflect.runtime.universe._

case class PartialQuery[T](q: JsObject, tool: Stylist[T], ordering: JsObject = Json.obj(), limit: Int = 0) {
  def where(field: String, value: JsValue) = {
    PartialQuery(q + (field -> value), tool)
  }

  def get() = {
    tool.get(this.q, ordering, limit)
  }

  def delete() = {
    tool.delete(this.q)
  }

  def first() = {
    tool.get(this.q, ordering).map (a => {
      if (a.length > 0)
        Some(a(0))
      else
        None
    })
  }

  def last() = {
    tool.get(this.q, ordering, limit).map (a => {
      if (a.length > 0)
        Some(a.last)
      else
        None
    })
  }

  def orderBy(field: String, order: String = "asc") = {
    order match {
      case "asc" => PartialQuery(q, tool, Json.obj(field -> Json.toJson(1)), limit)
      case "desc" => PartialQuery(q, tool, Json.obj(field -> Json.toJson(-1)), limit)
      case _ => PartialQuery(q, tool, ordering, limit)
    }
  }

  def limit(limit: Int) = {
    PartialQuery(q, tool, ordering, limit)
  }
}

sealed abstract class Direction
case class Foward() extends Direction
case class Backward() extends Direction

class ManyToMany[A <: RunwayModel[A], B <: RunwayModel[B]](pivot: String, o: A, dummy: B)(implicit readsA: Reads[A], writesA: Writes[A], readsB: Reads[B], writesB: Writes[B]) {

  var relationshipEvents: Map[String, (A, B) => Unit] = Map empty

  def on(trigger: String, action: ((A, B) => Unit)) = {
    relationshipEvents = relationshipEvents.updated(trigger, action);
  }

  def getAll(): Future[List[(String, String)]] = {
    def collection: JSONCollection = ReactiveMongoPlugin.db.collection[JSONCollection](pivot)

    val cursor: Cursor[JsObject] = collection.
        find(Json.obj()).
        cursor[JsObject]

      val related: Future[List[JsObject]] = cursor.toList

      related.map(fut => {
        fut.map(a => ((a \ "from").as[String] -> (a \ "to").as[String])).toList
      })
  }

  def getRelated(): Future[List[B]] = {
    def collection: JSONCollection = ReactiveMongoPlugin.db.collection[JSONCollection](pivot)

    val cursor: Cursor[JsObject] = collection.
        find(Json.obj("from" -> Json.toJson(o().id))).
        cursor[JsObject]

      val related: Future[List[JsObject]] = cursor.toList

      related.flatMap(fut => {
        dummy.find(fut.map(a => (a \ "to").as[String]))
      })
  }

  def getRelatedReverse(): Future[List[B]] = {
    def collection: JSONCollection = ReactiveMongoPlugin.db.collection[JSONCollection](pivot)

    val cursor: Cursor[JsObject] = collection.
        find(Json.obj("to" -> Json.toJson(o().id))).
        cursor[JsObject]

      val related: Future[List[JsObject]] = cursor.toList

      related.flatMap(fut => {
        dummy.find(fut.map(a => (a \ "from").as[String]))
      })
  }

  def isRelatedTo(target: String): Future[Boolean] = {
    def collection: JSONCollection = ReactiveMongoPlugin.db.collection[JSONCollection](pivot)

    val cursor: Cursor[JsObject] = collection.
        find(Json.obj("from" -> Json.toJson(o().id), "to" -> Json.toJson(target))).
        cursor[JsObject]

    val related: Future[List[JsObject]] = cursor.toList

    related.map(fut => {
      (fut.length > 0)
    })
  }

  def attach(toAttach: B) = {
    relationshipEvents.get("attach") match {
      case Some(event) => event(o, toAttach)
      case None => {}
    }
    def collection: JSONCollection = ReactiveMongoPlugin.db.collection[JSONCollection](pivot)
    val json = Json.obj("from" -> o().id, "to" -> toAttach().id)
    collection.save(json)
  }


  def attach(toAttachId: String) = {
    relationshipEvents.get("attach") match {
      case Some(event) => {
        dummy.find(toAttachId) onSuccess {
          case Some(a) => event(o, a)
          case None => {}
        }
      }
      case None => {}
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
      case Foward() => getRelated()
      case Backward() => getRelatedReverse()
    }
  }

}

class ModelNotFoundException(id: String) extends RuntimeException(id)

trait Jsonable[T] extends Runnable[T]{
  def jsonReads(p: JsValue): T
  def jsonWrites(): JsValue
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
    tool.find(id: String).map(f => {
      f match {
        case Some(a) => Some(a)
        case None    => throw new ModelNotFoundException(id)
      }
    })
  }

  def find(ids: List[String])(implicit reads: Reads[T], writes: Writes[T]) = tool.find(ids: List[String])

  def where(field: String, value: JsValue) = tool.where(field: String, value: JsValue)
}

trait RunwayModel[T] extends Jsonable[T]{ self: T =>

  def getModel = self

  val tool = new Stylist[T](getModel)

  var elegantEvents: Map[String, (RunwayModel[T] with T) => Unit] = Map empty

  def on(trigger: String, action: (RunwayModel[T] with T) => Unit) = {
    elegantEvents = elegantEvents.updated(trigger, action);
  }

  def apply() = self.getModel

  def save() {
    elegantEvents.get("save") match {
      case Some(event) => event(getModel)
      case None => {}
    }
    tool.save(getModel.id)
  }
}

trait RunwayModelCompanion[T] extends Runnable[T] { self: {def getModel: T with RunwayModel[T]} =>

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

object Main {

  def main(args: Array[String]) = {
    println("This does nothing")
  }

}