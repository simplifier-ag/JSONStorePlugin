package io.simplifier.jsonStore.slots

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import io.simplifier.jsonStore.helper.AbstractStoreBackend
import io.simplifier.jsonStore.permission.PermissionHandler
import io.simplifier.jsonStore.Constants._
import io.simplifier.jsonStore.RestMessages._
import AbstractStoreBackend.{DatabaseStoreType, MapDbStoreType}
import io.simplifier.jsonStore.controller.{JsonStoreController, UpdateActions}
import io.simplifier.pluginapi.UserSession
import io.simplifier.pluginapi.rest.PluginHeaders
import io.simplifier.pluginbase.PluginSettings
import io.simplifier.pluginbase.interfaces.AppServerDispatcher
import io.simplifier.pluginbase.slotservice.GenericSlotService
import io.simplifier.pluginbase.util.api.SuccessMessage
import org.json4s.{DefaultFormats, Formats}

class JsonStoreSlotService(jsonStoreController: JsonStoreController) extends GenericSlotService {
  override implicit val formats: Formats = DefaultFormats + UpdateActions.jsonSerializer
  val dbSlotNames: Seq[String] = Seq("migrate")

  override def slotNames: Seq[String] = Seq(
    "gracefullyShutdown", "gracefullyShutdownhttp",
    "collectionCreate",
    "collectionDelete",
    "collectionQuery",
    "storeCreate",
    "storeDelete",
    "storeQuery",
    "storeUpdate",
    "storeList") ++
    (jsonStoreController.storeBackend.storeType match {
      case DatabaseStoreType => dbSlotNames
      case _ => Nil
    })

  def standaloneOnlySlotNames: Option[Set[String]] = {
    jsonStoreController.storeBackend.storeType match {
      case DatabaseStoreType =>
        Some(dbSlotNames.toSet)
      case MapDbStoreType =>
        Some(slotNames.toSet)
    }
  }

  override def serviceRoute(implicit requestSource: PluginHeaders.RequestSource, userSession: UserSession): Route =
    jsonStoreController.storeBackend.storeType match {
      case DatabaseStoreType => clusterableRoute
      case _ => standaloneRoute
    }

  private def standaloneRoute(implicit requestSource: PluginHeaders.RequestSource, userSession: UserSession): Route = {
    path("gracefullyShutdownhttp" | "gracefullyShutdown") {
      complete(resultHandler(jsonStoreController.gracefullyShutdown, gracefullyShutdownJsonStoreFailure, ACTION_GRACEFULLY_SHUTDOWN, JSON_STORE))
    } ~
      path("collectionCreate") {
        requestHandler(jsonStoreController.collectionCreate, collectionCreateJsonStoreFailure, ACTION_COLLECTION_CREATE, JSON_STORE)
      } ~
      path("collectionDelete") {
        requestHandler(jsonStoreController.collectionDelete, collectionDeleteJsonStoreFailure, ACTION_COLLECTION_DELETE, JSON_STORE)
      } ~
      path("collectionQuery") {
        requestHandler(jsonStoreController.collectionQuery, collectionQueryJsonStoreFailure, ACTION_COLLECTION_QUERY, JSON_STORE)
      } ~
      path("storeCreate") {
        requestHandler(jsonStoreController.create, createJsonStoreFailure, ACTION_STORE_CREATE, JSON_STORE)
      } ~
      path("storeDelete") {
        requestHandler(jsonStoreController.delete, deleteJsonStoreFailure, ACTION_STORE_DELETE, JSON_STORE)
      } ~
      path("storeQuery") {
        requestHandler(jsonStoreController.query, queryJsonStoreFailure, ACTION_STORE_QUERY, JSON_STORE)
      } ~
      path("storeUpdate") {
        requestHandler(jsonStoreController.update, updateJsonStoreFailure, ACTION_STORE_UPDATE, JSON_STORE)
      } ~
      path("storeList") {
        requestHandler(jsonStoreController.list, listJsonStoreFailure, ACTION_STORE_LIST, JSON_STORE)
      }
  }

  private def clusterableRoute(implicit requestSource: PluginHeaders.RequestSource, userSession: UserSession): Route =
    standaloneRoute ~
      path("migrate") {
        complete(resultHandler(jsonStoreController.migrateToDatabase().map(_ => SuccessMessage("migrated")), migrateJsonStoreFailure, ACTION_MIGRATE, JSON_STORE))
      }

}

object JsonStoreSlotService {
  def apply(storeBackend: AbstractStoreBackend, dispatcher: AppServerDispatcher, pluginSettings: PluginSettings)
           (implicit materializer: Materializer): JsonStoreSlotService =
    new JsonStoreSlotService(new JsonStoreController(storeBackend, PermissionHandler(dispatcher, pluginSettings)))
}

