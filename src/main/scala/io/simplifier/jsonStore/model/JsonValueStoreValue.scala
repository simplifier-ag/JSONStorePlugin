package io.simplifier.jsonStore.model

import io.simplifier.jsonStore.util.data.Checksum
import io.simplifier.jsonStore.util.db.GenericDao
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.annotations.Column
import org.squeryl.{KeyedEntity, Table}
import org.squeryl.PrimitiveTypeMode._

import java.nio.charset.StandardCharsets

case class JsonValueStoreValue(@Column("id") id: String,
                               @Column("collection_id") collectionId: String,
                               @Column("key_text") key: String,
                               @Column("data") data: Array[Byte]) extends KeyedEntity[String]

object JsonValueStoreValue {

  def apply(collectionId: String, key: String, data: Array[Byte]): JsonValueStoreValue = {
    val id: String = encodeKey(collectionId, key)
    JsonValueStoreValue(id, collectionId, key, data)
  }

  def encodeKey(collectionId: String, key: String): String = {
    val item = (collectionId + key).getBytes(StandardCharsets.UTF_8)
    Checksum.checksumSHA256(item)
  }

}

class JsonValueStoreValueDao extends GenericDao[JsonValueStoreValue, String] {

  override def table: Table[JsonValueStoreValue] = PluginSchema.valueT

  /**
    * Find value via key (id is calculated from key and searched)
    *
    * @param key key of entity
    */
  def findByHashedKey(collectionId: String, key: String): Option[JsonValueStoreValue] = {
    val id: String = JsonValueStoreValue.encodeKey(collectionId, key)
    getById(id)
  }

  /**
    * Find all values that are member of given collection
    */
  def findAllInCollection(collectionId: String): Seq[JsonValueStoreValue] = {
    getAllBy(_.collectionId === collectionId)
  }

}