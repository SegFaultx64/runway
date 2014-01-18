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

class DbBehavior {
	lazy val db = ReactiveMongoPlugin.db
}

class Stylist[T](a: T with RunwayModel[T], preSlug: String = "", dbGen: DbBehavior = new DbBehavior()) {
  lazy val db = dbGen.db

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

      val futurePersonsList: Future[List[JsObject]] = cursor.collect[List]()

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

      val futurePersonsList: Future[List[JsObject]] = cursor.collect[List]()

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

      val futurePersonsList: Future[List[JsObject]] = cursor.collect[List]()

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

    collection.find(query).sort(order).cursor[JsObject].collect[List]().map(fut => {
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