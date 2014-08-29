package com.traversalsoftware.runway

// Concurrency Imports
import scala.concurrent.{ Future, ExecutionContext }
import scala.concurrent.ExecutionContext.Implicits.global

// Play Json imports
import play.api.libs.json._

case class PartialQuery[T](q: JsObject, tool: Stylist[T], ordering: JsObject = Json.obj(), limit: Int = 0) {
  def where(field: String, value: JsValue): PartialQuery[T] = {
    PartialQuery(q + (field -> value), tool)
  }

  def get(): Future[List[T]] = {
    tool.get(this.q, ordering, limit)
  }

  def delete() {
    tool.delete(this.q)
  }

  def first(): Future[Option[T]] = {
    tool.get(this.q, ordering).map(a ⇒ {
      a.headOption
    })
  }

  def last(): Future[Option[T]] = {
    tool.get(this.q, ordering, limit).map(a ⇒ {
      a.lastOption
    })
  }

  def orderBy(field: String, order: String = "asc"): PartialQuery[T] = {
    order match {
      case "asc"  ⇒ PartialQuery(q, tool, Json.obj(field -> Json.toJson(1)), limit)
      case "desc" ⇒ PartialQuery(q, tool, Json.obj(field -> Json.toJson(-1)), limit)
      case _      ⇒ this
    }
  }

  def limit(limit: Int): PartialQuery[T] = {
    PartialQuery(q, tool, ordering, limit)
  }
}
