package io.simplifier.jsonStore

object Constants {

  val JSON_STORE = "jsonStore"

  val ACTION_GRACEFULLY_SHUTDOWN = "gracefullyShutdown"
  val ACTION_COLLECTION_CREATE = "collectionCreate"
  val ACTION_COLLECTION_DELETE = "collectionDelete"
  val ACTION_COLLECTION_QUERY = "collectionCreate"

  val ACTION_STORE_CREATE = "storeCreate"
  val ACTION_STORE_DELETE = "storeDelete"
  val ACTION_STORE_QUERY = "storeQuery"
  val ACTION_STORE_UPDATE = "storeUpdate"
  val ACTION_STORE_LIST = "storeList"

  val ACTION_MIGRATE = "migrate"
  val ACTION_MIGRATED = "migrated"

  /*
   * Error codes
   */

  val JSON_STORE_COLLECTION_NOT_FOUND: String = "A-001"
  val JSON_STORE_COLLECTION_EXISTS: String = "A-002"
  val JSON_STORE_KEY_NOT_FOUND: String = "A-003"

}
