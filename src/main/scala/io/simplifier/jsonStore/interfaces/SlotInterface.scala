package io.simplifier.jsonStore.interfaces

import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import io.simplifier.jsonStore.helper.AbstractStoreBackend
import io.simplifier.jsonStore.slots.JsonStoreSlotService
import io.simplifier.jsonStore.permission.JsonStorePluginPermission.characteristicAdministrate
import io.simplifier.jsonStore.permission.PermissionHandler
import io.simplifier.jsonStore.slots
import io.simplifier.pluginapi.UserSession
import io.simplifier.pluginapi.rest.PluginHeaders
import io.simplifier.pluginapi.rest.PluginHeaders.RequestSource
import io.simplifier.pluginbase.interfaces.{AppServerDispatcher, SlotInterfaceService}
import io.simplifier.pluginbase.permission.PluginPermissionObject
import io.simplifier.pluginbase.{PluginDescription, PluginSettings}

class SlotInterface(jsonStoreSlotService: JsonStoreSlotService,
                    appServerDispatcher: AppServerDispatcher,
                    pluginDescription: PluginDescription,
                    pluginPermission: PluginPermissionObject,
                    permissionHandler: PermissionHandler)
  extends SlotInterfaceService(appServerDispatcher, pluginDescription, pluginPermission) {

  /** Base-URL relative to http service root */
  override val baseUrl: String = "slots"

  override def pluginSlotNames: Seq[String] =
    jsonStoreSlotService.slotNames

  override protected def checkAdministratePermission()(implicit userSession: UserSession, requestSource: RequestSource): Unit = {
    permissionHandler.checkAdditionalPermission(characteristicAdministrate)
  }

  override def standaloneOnlySlots: Option[Set[String]] = jsonStoreSlotService.standaloneOnlySlotNames

  /**
    * Plugin-specific inner route handling slot requests
    *
    * @param requestSource plugin request source
    * @param userSession   authenticated user session
    * @return service route
    */
  override def serviceRoute(implicit requestSource: PluginHeaders.RequestSource, userSession: UserSession): Route = {
    jsonStoreSlotService.serviceRoute
  }
}

object SlotInterface {

  def apply(storeBackend: AbstractStoreBackend, appServerDispatcher: AppServerDispatcher,
            pluginDescription: PluginDescription, pluginPermission: PluginPermissionObject, pluginSettings: PluginSettings)
           (implicit materializer: Materializer): SlotInterface = {
    new SlotInterface(slots.JsonStoreSlotService(storeBackend, appServerDispatcher, pluginSettings), appServerDispatcher,
      pluginDescription, pluginPermission, new PermissionHandler(appServerDispatcher, pluginSettings))
  }

}