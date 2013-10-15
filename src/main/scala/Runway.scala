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

class ManyToMany[A <: RunwayModel[A], B <: RunwayModel[B]](pivot: String, o: A, dummy: B) {
  
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
        find(Json.obj("from" -> Json.toJson(o().getId))).
        cursor[JsObject]

      val related: Future[List[JsObject]] = cursor.toList

      related.flatMap(fut => {
        dummy.find(fut.map(a => (a \ "to").as[String]))
      })
  }

  def getRelatedReverse(): Future[List[B]] = {
    def collection: JSONCollection = ReactiveMongoPlugin.db.collection[JSONCollection](pivot)

    val cursor: Cursor[JsObject] = collection.
        find(Json.obj("to" -> Json.toJson(o().getId))).
        cursor[JsObject]

      val related: Future[List[JsObject]] = cursor.toList

      related.flatMap(fut => {
        dummy.find(fut.map(a => (a \ "from").as[String]))
      })
  }

  def isRelatedTo(target: String): Future[Boolean] = {
    def collection: JSONCollection = ReactiveMongoPlugin.db.collection[JSONCollection](pivot)

    val cursor: Cursor[JsObject] = collection.
        find(Json.obj("from" -> Json.toJson(o().getId), "to" -> Json.toJson(target))).
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
    val json = Json.obj("from" -> o().getId, "to" -> toAttach().getId)
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
    val json = Json.obj("from" -> o().getId, "to" -> toAttachId)
    collection.save(json)
  }


  def detach(toDetach: B) = {
    def collection: JSONCollection = ReactiveMongoPlugin.db.collection[JSONCollection](pivot)
    val json = Json.obj("from" -> o().getId, "to" -> toDetach().getId)
    collection.remove(json)
  }


  def detach(toDetachId: String) = {
    def collection: JSONCollection = ReactiveMongoPlugin.db.collection[JSONCollection](pivot)
    val json = Json.obj("from" -> o().getId, "to" -> toDetachId)
    collection.remove(json)
  }

  def apply(direction: Direction = Foward()) = {
    direction match {
      case Foward() => getRelated()
      case Backward() => getRelatedReverse()
    }
  }

}

class DbBehavior {
  def apply() = {
    ReactiveMongoPlugin.db
  }
}

class Stylist[T](a: {def jsonReads(p: JsValue): T; def jsonWrites: JsValue}, preSlug: String = "", dbGen: DbBehavior = new DbBehavior()) {
  val db = dbGen()

  def slug = {
    if (preSlug != "") {
      preSlug
    } else {
      val temp = a.getClass.getSimpleName
      if (temp.last == '$') {
        temp.dropRight(1).toLowerCase + "s"
      } else {
        temp.toLowerCase + "s"
      }
    }
  }

  def find(id: String): Future[Option[T]] = {
    
    def collection: JSONCollection = db.collection[JSONCollection](slug)

    val cursor: Cursor[JsObject] = collection.
        find(Json.obj("_id" -> id)).
        cursor[JsObject]

      val futurePersonsList: Future[List[JsObject]] = cursor.toList

      // everything's ok! Let's reply with the array
      futurePersonsList.map(test => {
        if (test.length > 0) Some(a.jsonReads(test(0))) else None
      })
  }

  def find(ids: List[String]): Future[List[T]] = {
    
    def collection: JSONCollection = db.collection[JSONCollection](slug)

    val cursor: Cursor[JsObject] = collection.
        find(Json.obj("_id" -> Json.obj("$in" -> Json.toJson(ids)))).
        cursor[JsObject]

      val futurePersonsList: Future[List[JsObject]] = cursor.toList

      // everything's ok! Let's reply with the array
      futurePersonsList.map(fut => {
        fut.map( value => {
            a.jsonReads(value)
        })
      })
  }

  def all: Future[List[T]] = {
    
    def collection: JSONCollection = db.collection[JSONCollection](slug)

    val cursor: Cursor[JsObject] = collection.
        find(Json.obj()).
        cursor[JsObject]

      val futurePersonsList: Future[List[JsObject]] = cursor.toList

      // everything's ok! Let's reply with the array
      futurePersonsList.map(fut => {
        fut.map( value => {
            a.jsonReads(value)
        })
      })
  }

  def allQuery = {
    PartialQuery[T](Json.obj(), this)
  }

  def delete(q: JsObject) = {
    def collection: JSONCollection = db.collection[JSONCollection](slug)
    collection.remove(q)
  }

  def get(query: JsObject, order: JsObject = Json.obj(), limit: Int = 0): Future[List[T]] = {
    def collection: JSONCollection = db.collection[JSONCollection](slug)

    collection.find(query).sort(order).cursor[JsObject].toList.map(fut => {
      val newFuture = fut.map( value => {
          a.jsonReads(value)
      })
      if (limit > 0)
        newFuture.take(limit)
      else
        newFuture
    })
  }

  def where(field: String, value: JsValue) = {
    PartialQuery[T](Json.obj(field -> value), this)
  }

  def save(id: String) {
    def collection: JSONCollection = db.collection[JSONCollection](slug)
    val json = (a.jsonWrites.as[JsObject] + ("_id", Json.toJson(id)))
    collection.save(json)
  }

}

class ModelNotFoundException(id: String) extends RuntimeException(id)

trait RunwayModel[T]{ self: {def getModel: {def jsonReads(p: JsValue): T; def jsonWrites: JsValue; def getId: String}} =>
  val tool = new Stylist[T](getModel)

  var elegantEvents: Map[String, ({def jsonReads(p: JsValue): T; def jsonWrites: JsValue; def getId: String}) => Unit] = Map empty

  def on(trigger: String, action: ({def jsonReads(p: JsValue): T; def jsonWrites: JsValue; def getId: String}) => Unit) = {
    elegantEvents = elegantEvents.updated(trigger, action);
  }

  def apply() = self.getModel

  def all = tool.all

  def allQuery = tool.allQuery

  def find(id: String) = {
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

  def find(ids: List[String]) = tool.find(ids: List[String])
  
  def save() { 
    elegantEvents.get("save") match {
      case Some(event) => event(self.getModel)
      case None => {}
    }
    tool.save(self.getModel.getId)
  }

  def where(field: String, value: JsValue) = tool.where(field: String, value: JsValue)
}

trait RunwayModelCompanion[T] extends RunwayModel[T] { self: {def getModel: {def jsonReads(p: JsValue): T; def jsonWrites: JsValue; def getId: String}} =>
  override val tool = new Stylist[T](self.getModel, getSlug)
  
  def getSlug = {
    val temp = self.getClass.getSimpleName
    if (temp.last == '$') {
      temp.dropRight(1).toLowerCase + "s"
    } else {
      temp.toLowerCase + "s"
    }
  }

  override def save() = throw new UnsupportedOperationException()
  
  def save(toSave: {def jsonReads(p: JsValue): T; def jsonWrites: JsValue; def getId: String}) { 
    val tool = new Stylist[T](toSave)
    tool.save(toSave.getId)
  }
}