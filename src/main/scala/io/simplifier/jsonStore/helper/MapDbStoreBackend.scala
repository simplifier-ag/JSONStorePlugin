package io.simplifier.jsonStore.helper

import com.typesafe.config.Config
import io.simplifier.jsonStore.helper.AbstractStoreBackend.MapDbStoreType
import io.simplifier.jsonStore.controller.JsonStoreController.{CollectionAlreadyExists, CollectionDoesNotExists}
import AbstractStoreBackend.MapDbStoreType
import io.simplifier.pluginbase.slotservice.GenericFailureHandling.OperationFailureMessage
import org.mapdb._

import scala.collection.JavaConverters._

/**
  * Json Store backend using MapDB (storing data in file in filesystem)
  */
class MapDbStoreBackend(config: Config) extends AbstractStoreBackend(config, MapDbStoreType) {

  import MapDbStoreBackend._

  class Collection(name: String) {

    protected var mapdbCollection: Option[ValueMap] = None

    def exists: Boolean = db.exists(name)

    def open(): Option[ValueMap] = if (exists)
      Some(create())
    else
      None

    def delete(): Unit = {
      db.delete(name)
      mapdbCollection = None
    }

    def create(): ValueMap = {
      mapdbCollection = Some(initNamedMapDBStore(db, name))
      mapdbCollection.get
    }

    def get: Option[ValueMap] = mapdbCollection

    def close(): Unit = mapdbCollection foreach (item => item.close())
  }


  val db: DB = initMapDB()

  override def shutdown(): Unit = db.close()

  override def createCollection(name: String): Unit = {
    val collection = new Collection(name)
    if (collection.exists) {
      throw CollectionAlreadyExists(name)
    } else {
      collection.create()
      collection.close()
    }
  }

  override def deleteCollection(name: String): Unit = {
    val collection = new Collection(name)
    if (!collection.exists)
      throw CollectionDoesNotExists(name)
    else {
      collection.open()
      collection.delete()
      collection.close()
    }
  }

  override def checkCollectionExists(name: String): Boolean = {
    val collection = new Collection(name)
    collection.exists
  }

  override def get(collectionName: String, key: String): Option[Array[Byte]] =
    withCollection(collectionName) { valueMap =>
      if (valueMap.containsKey(key))
        Some(valueMap.get(key))
      else
        None
    }

  override def list(collectionName: String): Map[String, Array[Byte]] = {
    try {
      withCollection(collectionName) { collection =>
        collection.snapshot().asScala.toMap
      }
    } catch {
      case _: OperationFailureMessage => Map.empty
    }
  }

  override def put(collectionName: String, key: String, values: Array[Byte]): Unit = {
    withCollection(collectionName) { valueMap =>
      valueMap.put(key, values)
      db.commit();
    }
  }

  override def delete(collectionName: String, key: String): Unit = {
    withCollection(collectionName) { valueMap =>
      valueMap.remove(key)
    }
  }

  /**
    * Migrate file store top database
    */
  override def migrateToDatabase(): Unit = new NotImplementedError("Migration cannot be executed with MapDb backend")

  private def useCollection(name: String): Option[ValueMap] = {
    val col = new Collection(name)
    col.open() match {
      case Some(valueMap) => Some(valueMap)
      case _ => None
    }
  }

  def withCollection[T](collectionName: String)(block: ValueMap => T): T = {
    useCollection(collectionName) match {
      case None => throw CollectionDoesNotExists(collectionName)
      case Some(collection) =>
        val result = block(collection)
        collection.close()
        result
    }
  }

}

object MapDbStoreBackend {

  type ValueMap = HTreeMap[String, Array[Byte]]

}
