package io.simplifier.jsonStore.controller

import io.simplifier.jsonStore.util.data.CaseEnumeration
import io.simplifier.pluginbase.util.json.NamedTupleAccess._
import org.json4s.{Formats, JField, JInt, JObject, JString, JValue, TypeInfo}

import scala.reflect.ClassTag


/**
  * The Update Actions.
  */
object UpdateActions extends CaseEnumeration(false) {


  /**
    * The update actions enumeration type.
    */
  override type EnumVal = UpdateAction


  /**
    * The update action.
    *
    * @param id         the id.
    * @param name       the name.
    * @param namePretty the pretty name (for logs).
    */
  class UpdateAction private[UpdateActions](val id: Int,
                                            val name: String,
                                            val namePretty: String) extends Value {
    def toJson: JValue = {
      JObject(
        JField("id", JInt(id)),
        JField("name", JString(name)),
        JField("namePretty", JString(namePretty))
      )
    }

    override def toString: String = namePretty
  }


  /** The undefined state (fallback) */
  final val UNDEFINED: UpdateAction = new UpdateAction(-1, "Undefined", "Undefined")

  /** The add action */
  final val ADD: UpdateAction = new UpdateAction(0, "Add", "Add")

  /** The delete action */
  final val DELETE: UpdateAction = new UpdateAction(1, "Delete", "Delete")


  def apply(parameter: String): UpdateAction = {
    withNameOpt(parameter)
      .getOrElse(UNDEFINED)
  }


  def apply(id: Int): UpdateAction = {
    id match {
      case ADD.id => ADD
      case DELETE.id => DELETE
      case _ => UNDEFINED
    }
  }


  def apply(value: JValue): UpdateAction = {
    value match {
      case JString(str) => apply(str)
      case JInt(int) => apply(int.intValue())
      case _ => UNDEFINED
    }
  }


  override protected def jsonDeserialize(implicit formats: Formats, ct: ClassTag[UpdateAction]): PartialFunction[(TypeInfo, JValue), UpdateAction] = {
    case (_@TypeInfo(clazz, _), json) if ct.runtimeClass.isAssignableFrom(clazz) =>
      json match {
        case JObject(jFields) if jFields.exists(field => field.name == "name") => jFields.collectFirst { case JField(name, value) if name == "name" => apply(value) }.getOrElse(UNDEFINED)
        case value => apply(value)
      }
  }


  override protected def jsonSerialize(implicit format: Formats, ct: ClassTag[UpdateAction]): PartialFunction[Any, JValue] = {
    case ct(value) => JString(value.name)
  }


}
