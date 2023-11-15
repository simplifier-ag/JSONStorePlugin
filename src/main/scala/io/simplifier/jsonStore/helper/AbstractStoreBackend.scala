package io.simplifier.jsonStore.helper

import AbstractStoreBackend.StoreType
import com.typesafe.config.Config
import org.mapdb.{DB, DBMaker, HTreeMap, Serializer}

import java.io.File

/**
  * Abstract backend for Json Store backend, defining interface for different store backend implementations.
  * Also contains logic to initialize mapdb storage
  */
abstract class AbstractStoreBackend(config: Config, val storeType: StoreType) {

  /**
    * filename of storage file from config used by MapDb Backend or migration to database
    */
  lazy val mapDbFileName: String = config.getString("plugin.filename")

  /**
    * Init MapDB, either as Storage used in [[MapDbStoreBackend]], or during migration to DB.
    *
    * @return opened MapDB instance
    */
  protected def initMapDB(): DB = {
    DBMaker.fileDB(new File(mapDbFileName))
      .compressionEnable()
      .fileMmapEnable()
      .checksumEnable()
      .snapshotEnable()
      .make()
  }

  /**
    * Init MapDB Store, either as Storage used in [[MapDbStoreBackend]], or during migration to DB.
    *
    * @param db   opened MapDB instance
    * @param name name of initialized collection
    * @return opened MapDB Store instance
    */
  protected def initNamedMapDBStore(db: DB, name: String): HTreeMap[String, Array[Byte]] = {
    db.hashMapCreate(name)
      .keySerializer(Serializer.STRING)
      .valueSerializer(new Serializer.CompressionWrapper(Serializer.BYTE_ARRAY))
      .makeOrGet()
  }

  /**
    * Create a new collection with given name.
    * If the collection already exists an error is thrown
    */
  def createCollection(name: String): Unit

  /**
    * Deletes the collection with given name.
    *
    */
  def deleteCollection(name: String): Unit

  /**
    * Check if collection with name exists
    */
  def checkCollectionExists(name: String): Boolean

  /**
    * Gets the value of key in collection as byte array
    */
  def get(collection: String, key: String): Option[Array[Byte]]

  /**
    * Get the values of collection filtered by query and queryValue
    */
  def list(collectionName: String): Map[String, Array[Byte]]

  /**
    * Update/create key in collection with given value
    */
  def put(collectionName: String, key: String, values: Array[Byte]): Unit

  /**
    * Delete key in collection
    */
  def delete(collectionName: String, key: String): Unit

  /**
    * Migrate file store top database
    */
  def migrateToDatabase(): Unit

  /**
    * Shutdown connected store
    */
  def shutdown(): Unit = {}

  /**
    * Initialize Backend
    */
  def init(): Unit = {}

}

object AbstractStoreBackend {

  /**
    * Types of implementations of Storage Backends
    */
  sealed trait StoreType

  case object DatabaseStoreType extends StoreType

  case object MapDbStoreType extends StoreType

}
