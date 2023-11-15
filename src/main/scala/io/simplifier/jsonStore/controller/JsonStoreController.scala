package io.simplifier.jsonStore.controller

import akka.stream.Materializer
import UpdateActions.{ADD, DELETE, UpdateAction}
import io.simplifier.jsonStore.helper.{AbstractStoreBackend, JsonUtil}
import io.simplifier.jsonStore.{Constants, JsonStorePlugin}
import io.simplifier.jsonStore.permission.PermissionHandler
import io.simplifier.jsonStore.permission.JsonStorePluginPermission.{characteristicAdministrate, characteristicEdit, characteristicView}
import io.simplifier.pluginapi.UserSession
import io.simplifier.pluginapi.rest.PluginHeaders.RequestSource
import io.simplifier.pluginbase.slotservice.GenericFailureHandling.OperationFailureMessage
import io.simplifier.pluginbase.util.api.ApiMessage
import io.simplifier.pluginbase.util.json.SimplifierFormats
import org.json4s._

import scala.util.{Success, Try}

/**
  * Controller for Json Store
  */
class JsonStoreController(val storeBackend: AbstractStoreBackend, permissionHandler: PermissionHandler)
                         (implicit materializer: Materializer) extends SimplifierFormats {

  import JsonStoreController._

  /**
    * Create a new collection
    */
  def collectionCreate(request: CollectionCreate)(implicit userSession: UserSession, requestSource: RequestSource): Try[Result] = Try {
    permissionHandler.checkAdditionalPermission(characteristicEdit)
    storeBackend.createCollection(request.name)
    new Result("collection created")
  }

  /**
    * Delete a collection with all its connected json values
    */
  def collectionDelete(request: CollectionDelete)(implicit userSession: UserSession, requestSource: RequestSource): Try[Result] = Try {
    permissionHandler.checkAdditionalPermission(characteristicEdit)
    storeBackend.deleteCollection(request.name)
    new Result("collection deleted")
  }

  /**
    * Check if a collection exists
    */
  def collectionQuery(request: CollectionQuery)(implicit userSession: UserSession, requestSource: RequestSource): Try[Result] = Try {
    permissionHandler.checkAdditionalPermission(characteristicView)
    val result: Boolean = storeBackend.checkCollectionExists(request.name)
    new Result(QueryResult(result))
  }

  /**
    * List all contents of a collection, the result can be filtered by the query and queryValue parameters
    */
  def list(request: StoreList)(implicit userSession: UserSession, requestSource: RequestSource): Try[Result] = Try {
    permissionHandler.checkAdditionalPermission(characteristicView)
    // mapValues does not return a new map, which can lead to performance issues. By .view.force the returned view is turned into a new map
    val allAsJValue: Map[String, JValue] = storeBackend.list(request.collection).mapValues(JsonUtil.asJValue).view.force
    val filteredResult: Option[Map[String, JValue]] = JsonUtil.filterJsonValues(allAsJValue, request.query, request.queryValue)
    val result = filteredResult getOrElse allAsJValue
    new Result(result)
  }

  /**
    * Create a new value in a collection
    */
  def create(request: StoreCreate)(implicit userSession: UserSession, requestSource: RequestSource): Try[Result] = Try {
    permissionHandler.checkAdditionalPermission(characteristicEdit)
    storeBackend.put(request.collection, request.key, JsonUtil.asBytes(request.values))
    new Result("Ok")
  }

  /**
    * Update an existing value of a collection, action parameter defines if the given values are added or removed.
    */
  def update(request: StoreUpdate)(implicit userSession: UserSession, requestSource: RequestSource): Try[Result] = Try {
    permissionHandler.checkAdditionalPermission(characteristicEdit)
    val putValue: JValue => Unit = json => storeBackend.put(request.collection, request.key, JsonUtil.asBytes(json))

    val oldValue = storeBackend.get(request.collection, request.key).map(JsonUtil.asJValue)
    (request.action, oldValue) match {
      case (ADD, Some(jValue)) =>
        val updatedJson = JsonUtil.mergeJSON(jValue :: request.values :: Nil)
        putValue(updatedJson)
      case (ADD, None) =>
        putValue(request.values)
      case (DELETE, Some(jValue)) =>
        val updatedJson = JsonUtil.deleteFromJSON(jValue, request.values)
        putValue(updatedJson)
      case _ =>
      // nothing to delete
    }
    new Result("Ok")
  }

  /**
    * Delete a value from a collection
    */
  def delete(request: StoreDelete)(implicit userSession: UserSession, requestSource: RequestSource): Try[Result] = Try {
    permissionHandler.checkAdditionalPermission(characteristicEdit)
    storeBackend.delete(request.collection, request.key)
    new Result("Ok")
  }

  /**
    * Gets the json value for a key in a collection
    */
  def query(request: StoreQuery)(implicit userSession: UserSession, requestSource: RequestSource): Try[Result] = Try {
    permissionHandler.checkAdditionalPermission(characteristicView)
    val resultOpt: Option[Array[Byte]] = storeBackend.get(request.collection, request.key)
    val jsonResult = resultOpt.map(JsonUtil.asJValue).getOrElse(JNothing)
    new Result(jsonResult)
  }

  /**
    * Migrates the content of the MapDB file to the database (only available in database backend)
    */
  def migrateToDatabase()(implicit userSession: UserSession, requestSource: RequestSource): Try[Unit] = Try {
    permissionHandler.checkAdditionalPermission(characteristicAdministrate)
    storeBackend.migrateToDatabase()
  }

  /**
    * Shuts down the plugin
    */
  def gracefullyShutdown()(implicit userSession: UserSession, requestSource: RequestSource): Try[ApiMessage] = {
    permissionHandler.checkAdditionalPermission(characteristicAdministrate)
    storeBackend.shutdown()
    JsonStorePlugin.terminateBySystemShutdown()
    Success(new Result("ByeBye"))
  }

}

object JsonStoreController {

  case class CollectionCreate(name: String) extends ApiMessage

  case class CollectionDelete(name: String) extends ApiMessage

  case class CollectionQuery(name: String) extends ApiMessage

  case class QueryResult(exists: Boolean)

  case class StoreList(collection: String, query: Option[String], queryValue: Option[String]) extends ApiMessage

  case class StoreCreate(collection: String, key: String, values: JValue) extends ApiMessage

  case class StoreUpdate(collection: String, key: String, values: JValue, action: UpdateAction) extends ApiMessage

  case class StoreDelete(collection: String, key: String) extends ApiMessage

  case class StoreQuery(collection: String, key: String) extends ApiMessage


  case class Result(result: JValue, success: Boolean) extends ApiMessage {
    def this(result: JValue) = this(result, true)

    def this(result: String) = this(JString(result))

    def this(result: Any) = this(Extraction.decompose(result)(DefaultFormats.lossless))
  }

  /**
    * Failure if a collection does not exist (error code A-001)
    */
  def CollectionDoesNotExists(name: String): OperationFailureMessage =
    OperationFailureMessage(s"collection '$name' not exists", Constants.JSON_STORE_COLLECTION_NOT_FOUND)

  /**
    * Failure if a collection already exists (error code A-002)
    */
  def CollectionAlreadyExists(name: String): OperationFailureMessage =
    OperationFailureMessage(s"collection '$name' already exists", Constants.JSON_STORE_COLLECTION_EXISTS)

  /**
    * Failure if a key does not exist in a collection (error code A-003)
    */
  def KeyNotFoundInCollection(collection: String, key: String): OperationFailureMessage =
    OperationFailureMessage(s"key '$key' not found in collection '$collection'", Constants.JSON_STORE_KEY_NOT_FOUND)

}