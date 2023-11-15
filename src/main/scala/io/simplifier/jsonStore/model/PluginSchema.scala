package io.simplifier.jsonStore.model

import io.simplifier.jsonStore.util.db.SquerylInit
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.{PrimitiveTypeMode, Schema, Table}

/**
  * DB Schema for Plugin Data Model.
  */
object PluginSchema extends PluginSchema

/**
  * DB Schema for Plugin Data Model.
  */
class PluginSchema extends Schema {

  val prefix: String = SquerylInit.tablePrefix.getOrElse("")

  /*
   * Tables
   */

  val collectionT: Table[JsonValueStoreCollection] = table[JsonValueStoreCollection](prefix + "Json_Store_Collection")

  val valueT: Table[JsonValueStoreValue] = table[JsonValueStoreValue](prefix + "Json_Store_Value")

  val valueToCollectionRelation: PrimitiveTypeMode.OneToManyRelationImpl[JsonValueStoreCollection, JsonValueStoreValue] =
    oneToManyRelation(collectionT, valueT).via((col, v) => col.id === v.collectionId)
  valueToCollectionRelation.foreignKeyDeclaration.constrainReference(onDelete.cascade)


}
