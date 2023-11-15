package io.simplifier.jsonStore.model

import io.simplifier.jsonStore.util.data.Checksum
import io.simplifier.jsonStore.util.db.GenericDao
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.annotations.Column
import org.squeryl.{KeyedEntity, Table}

import java.nio.charset.StandardCharsets

case class JsonValueStoreCollection(@Column("id") id: String,
                                    @Column("name") name: String) extends KeyedEntity[String]

object JsonValueStoreCollection {

  def apply(name: String): JsonValueStoreCollection = {
    val id: String = encodeKey(name)
    JsonValueStoreCollection(id, name)
  }

  def encodeKey(name: String): String = {
    val item = name.getBytes(StandardCharsets.UTF_8)
    Checksum.checksumSHA256(item)
  }

}

class JsonValueStoreCollectionDao extends GenericDao[JsonValueStoreCollection, String] {

  override def table: Table[JsonValueStoreCollection] = PluginSchema.collectionT

  /**
    * Find value via name (id is calculated from name and searched)
    *
    * @param name name of collection
    * @return
    */
  def findByHashedName(name: String): Option[JsonValueStoreCollection] = {
    val id: String = JsonValueStoreCollection.encodeKey(name)
    getById(id)
  }

  /**
    * Check if given name exists in the database without selecting large fields (id is calculated from name and searched)
    *
    * @param name name of entity
    */
  def checkHashedKeyExists(name: String): Boolean = inTransaction {
    val id: String = JsonValueStoreCollection.encodeKey(name)
    from(table)(c => where(c.id === id) select c.id).headOptionFixed.isDefined
  }

}