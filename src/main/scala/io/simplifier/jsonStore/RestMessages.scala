package io.simplifier.jsonStore

import Constants._
import io.simplifier.pluginbase.slotservice.Constants.{ACTION_CREATED, ACTION_DELETED, ACTION_LISTED, ACTION_RETURNED, ACTION_UPDATED}
import io.simplifier.pluginbase.slotservice.GenericRestMessages

object RestMessages extends GenericRestMessages {

  val (collectionCreateJsonStoreSuccess, collectionCreateJsonStoreFailure) = mkRestMessagePair(JSON_STORE, ACTION_CREATED)
  val (collectionDeleteJsonStoreSuccess, collectionDeleteJsonStoreFailure) = mkRestMessagePair(JSON_STORE, ACTION_DELETED)
  val (collectionQueryJsonStoreSuccess, collectionQueryJsonStoreFailure) = mkRestMessagePair(JSON_STORE, ACTION_RETURNED)
  val (createJsonStoreSuccess, createJsonStoreFailure) = mkRestMessagePair(JSON_STORE, ACTION_CREATED)
  val (deleteJsonStoreSuccess, deleteJsonStoreFailure) = mkRestMessagePair(JSON_STORE, ACTION_DELETED)
  val (queryJsonStoreSuccess, queryJsonStoreFailure) = mkRestMessagePair(JSON_STORE, ACTION_RETURNED)
  val (updateJsonStoreSuccess, updateJsonStoreFailure) = mkRestMessagePair(JSON_STORE, ACTION_UPDATED)
  val (listJsonStoreSuccess, listJsonStoreFailure) = mkRestMessagePair(JSON_STORE, ACTION_LISTED)


  val (gracefullyShutdownJsonStoreSuccess, gracefullyShutdownJsonStoreFailure) = mkRestMessagePair(JSON_STORE, ACTION_GRACEFULLY_SHUTDOWN)
  val (migrateJsonStoreSuccess, migrateJsonStoreFailure) = mkRestMessagePair(JSON_STORE, ACTION_MIGRATED)

}
