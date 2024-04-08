package io.simplifier.jsonStore.helper

import com.typesafe.config.Config
import io.simplifier.jsonStore.controller.JsonStoreController.{CollectionAlreadyExists, CollectionDoesNotExists, KeyNotFoundInCollection}
import io.simplifier.jsonStore.helper.AbstractStoreBackend.DatabaseStoreType
import io.simplifier.jsonStore.model._
import io.simplifier.jsonStore.util.db.{DatabaseMigration, SquerylInit}
import io.simplifier.pluginbase.model.UserTransaction
import org.mapdb.{DB, HTreeMap}

/**
  * Implementation of storage backend with database access.
  *
  * @param config          configuration containing map db storage path (for migration) and database configuration
  * @param collectionDao   dao for collections
  * @param jsonValueDao    dao for key JSON-value pairs
  * @param userTransaction database transaction factory
  */
class DatabaseStoreBackend(config: Config,
                           collectionDao: JsonValueStoreCollectionDao,
                           jsonValueDao: JsonValueStoreValueDao,
                           userTransaction: UserTransaction)
  extends AbstractStoreBackend(config: Config, DatabaseStoreType) {

  override def init(): Unit = {
    SquerylInit.initWith(SquerylInit.parseConfig(config))
    DatabaseMigration(config, PluginSchema).runMigration()
  }

  override def createCollection(name: String): Unit = userTransaction.inSingleTransaction {
    collectionDao.findByHashedName(name) match {
      case Some(_) => throw CollectionAlreadyExists(name)
      case None =>
        val model: JsonValueStoreCollection = JsonValueStoreCollection(name)
        collectionDao.insert(model)
    }
  }.get

  override def deleteCollection(name: String): Unit = userTransaction.inSingleTransaction {
    val collection = findCollectionRequired(name)
    collectionDao.delete(collection.id)
  }.get

  override def checkCollectionExists(name: String): Boolean = {
    collectionDao.findByHashedName(name) match {
      case Some(_) => true
      case None => false
    }
  }

  override def get(collectionName: String, key: String): Option[Array[Byte]] = userTransaction.inSingleTransaction {
    val collection = findCollectionRequired(collectionName)
    jsonValueDao.findByHashedKey(collection.id, key).map(_.data)
  }.get

  override def list(collectionName: String): Map[String, Array[Byte]] = userTransaction.inSingleTransaction {
    val collection = findCollectionRequired(collectionName)
    val valuesInCollection: Seq[JsonValueStoreValue] = jsonValueDao.findAllInCollection(collection.id)
    valuesInCollection.map(model => model.key -> model.data).toMap
  }.get

  override def put(collectionName: String, key: String, value: Array[Byte]): Unit = userTransaction.inSingleTransaction {
    val collection = findCollectionRequired(collectionName)
    jsonValueDao.findByHashedKey(collection.id, key) match {
      case Some(model) => jsonValueDao.update(model.copy(data = value))
      case None => jsonValueDao.insert(JsonValueStoreValue(collection.id, key, value))
    }
  }.get

  override def delete(collectionName: String, key: String): Unit = userTransaction.inSingleTransaction {
    val collection = findCollectionRequired(collectionName)
    jsonValueDao.findByHashedKey(collection.id, key) match {
      case Some(oldValue) =>
        jsonValueDao.delete(oldValue.id)
      case None =>
        throw KeyNotFoundInCollection(collectionName, key)
    }
  }.get

  override def migrateToDatabase(): Unit = {
    val mapDb: DB = initMapDB()
    userTransaction.inSingleTransaction {
      try {

        mapDb.getAll.forEach {
          case (collectionName, _) =>
            val collection: JsonValueStoreCollection = collectionDao.findByHashedName(collectionName) match {
              case Some(col) => col
              case None =>
                val col = JsonValueStoreCollection(collectionName)
                collectionDao.insert(col)
                col
            }

            val collectionDbStore: HTreeMap[String, Array[Byte]] = initNamedMapDBStore(mapDb, collectionName)

            collectionDbStore.forEach {
              case (key, value) =>
                val model = JsonValueStoreValue(collection.id, key, value)
                jsonValueDao.findByHashedKey(collection.id, key) match {
                  case Some(_) => jsonValueDao.update(model)
                  case None => jsonValueDao.insert(model)
                }
            }
        }

      } finally {
        mapDb.close()
      }
    }.get
  }


  private def findCollectionRequired(collectionName: String): JsonValueStoreCollection = {
    collectionDao.findByHashedName(collectionName).getOrElse(throw CollectionDoesNotExists(collectionName))
  }

}
