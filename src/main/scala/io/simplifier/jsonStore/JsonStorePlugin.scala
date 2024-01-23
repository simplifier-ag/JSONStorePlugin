package io.simplifier.jsonStore

import io.simplifier.jsonStore.helper.{AbstractStoreBackend, DatabaseStoreBackend}
import io.simplifier.jsonStore.interfaces.SlotInterface
import io.simplifier.jsonStore.model.{JsonValueStoreCollectionDao, JsonValueStoreValueDao}
import io.simplifier.jsonStore.permission.JsonStorePluginPermission
import io.simplifier.pluginbase.interfaces.PluginBaseHttpService
import io.simplifier.pluginbase.model.UserTransaction
import io.simplifier.pluginbase.permission.PluginPermissionObject
import io.simplifier.pluginbase.util.config.ConfigExtension._
import io.simplifier.pluginbase.{SimplifierPlugin, SimplifierPluginLogic}

import scala.concurrent.Future



object JsonStorePlugin extends SimplifierPluginLogic(
  Defaults.PLUGIN_DESCRIPTION_DEFAULT, "jsonStore") with SimplifierPlugin {

  import ACTOR_SYSTEM.dispatcher

  val pluginSecret: String = byDeployment.PluginRegistrationSecret()

  var storeBackend: AbstractStoreBackend = _

  val permission = JsonStorePluginPermission

  /**
   * The plugin permissions
   *
   * @return a sequence of the plugin permissions
   */
  override def pluginPermissions: Seq[PluginPermissionObject] = Seq(permission)

  override def stopPluginServices(): Future[Unit] = Future {
    storeBackend.shutdown()
  }

  override def startPluginServices(basicState: SimplifierPlugin.BasicState): Future[PluginBaseHttpService] = Future {

    storeBackend = new DatabaseStoreBackend(basicState.config, new JsonValueStoreCollectionDao, new JsonValueStoreValueDao, new UserTransaction)

    storeBackend.init()
    val slotInterface = Some(interfaces.SlotInterface(storeBackend, basicState.dispatcher, basicState.pluginDescription, permission,
      basicState.settings))
    val proxyInterface = None
    val configInterface = None
    new PluginBaseHttpService(basicState.pluginDescription, basicState.settings, basicState.appServerInformation,
      proxyInterface, slotInterface, configInterface)
  }

}

