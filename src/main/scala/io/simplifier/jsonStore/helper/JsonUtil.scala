package io.simplifier.jsonStore.helper

import io.simplifier.jsonStore.util.json.JSONCompatibility.LegacySearch
import io.simplifier.pluginbase.util.json.JSONCompatibility.parseJsonOrEmptyString
import io.simplifier.pluginbase.util.json.JSONFormatter.renderJSONPretty
import io.simplifier.pluginbase.util.json.NamedTupleAccess._
import org.json4s._
import java.nio.charset.StandardCharsets


/**
  * Util for JSON operations required for JSON Store Plugin like conversion to and from bytes, merge, traversal, ...
  */
object JsonUtil {

  def asBytes(jValue: JValue): Array[Byte] = renderJSONPretty(jValue).getBytes(StandardCharsets.UTF_8)

  def asJValue(bytes: Array[Byte]): JValue = parseJsonOrEmptyString(new String(bytes, StandardCharsets.UTF_8))

  def mergeJSON(parameterList: List[JValue]): JValue = {
    var res: JValue = JNothing
    parameterList.foreach(item => res = res merge item)
    res
  }

  def mkPath(value: JValue,
             prefix: List[String] = Nil): List[List[String]] = value match {
    case JObject(obj) => if (obj.nonEmpty)
      obj flatMap {
        case JField(n, v) => mkPath(v, prefix ::: List(n))
      }
    else
      List(prefix)
    case _ =>
      List(prefix)
  }

  def createNewJson(from: JValue,
                    deleteItems: Set[List[String]],
                    prefix: List[String] = Nil): JValue =
    if (deleteItems contains prefix)
      JNothing
    else from match {
      case JObject(obj) if obj.nonEmpty =>
        JObject(obj map {
          case JField(name, value) =>
            JField(name, createNewJson(value, deleteItems, prefix ::: List(name)))
        } filter {
          _.value != JNothing
        })
      case json => json
    }

  def deleteFromJSON(src: JValue, delete: JValue): JValue =
    createNewJson(src, mkPath(delete).toSet)

  def filterJsonValues(result: Map[String, JValue], queryOpt: Option[String], queryValueOpt: Option[String]): Option[Map[String, JValue]] = {
    for {
      query <- queryOpt
      queryValue <- queryValueOpt
    } yield {
      result filter {
        case (_, value) =>
          val resolvedJson = traverseJson(value, query)
          resolvedJson != JNothing && resolvedJson.values.toString == queryValue
      }
    }
  }

  def traverseJson(root: JValue, queryPath: String): JValue = {
    val queryPaths = queryPath split "/"
    queryPaths.foldLeft(root) {
      case (jObj: JObject, segment) =>  LegacySearch(jObj) \ segment
      case _ => JNothing
    }
  }


}
