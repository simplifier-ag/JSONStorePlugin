package io.simplifier.jsonStore.permission

import akka.http.scaladsl.model.StatusCodes
import io.simplifier.pluginapi.rest.PluginHeaders.RequestSource
import io.simplifier.pluginapi.{GrantedPermission, UserSession}
import io.simplifier.pluginbase.PluginSettings
import io.simplifier.pluginbase.helpers.SimplifierServerSettings
import io.simplifier.pluginbase.interfaces.AppServerDispatcher
import io.simplifier.pluginbase.permission.PluginPermissionObject
import io.simplifier.pluginbase.slotservice.Constants.ERROR_CODE_MISSING_PERMISSION
import io.simplifier.pluginbase.slotservice.GenericFailureHandling.OperationFailureMessage
import io.simplifier.pluginbase.util.logging.Logging

import scala.concurrent.Await

class PermissionHandler(appServerDispatcher: AppServerDispatcher, pluginSettings: PluginSettings) extends Logging {

  import PermissionHandler._

  /**
    * Check if the user has the required permission
    *
    * @param characteristic the required characteristic to check
    * @param expectedValue  the expected value of the characteristic
    * @param userSession    the implicit user session of the logged in user
    * @param requestSource  the request source
    */
  def checkPermission(characteristic: String, expectedValue: String = "true")
                     (implicit userSession: UserSession, requestSource: RequestSource): Unit = {
    val permissions = loadPermissions()

    val hasPermission = permissions.flatMap { perm =>
      perm.characteristics.getOrElse(characteristic, Set())
    } contains expectedValue

    if (!hasPermission) throw MissingPermissionException
  }

  /**
    * If the plugin permission check is enabled check if the user has the required permission
    *
    * @param characteristic the required characteristic to check
    * @param userSession    the implicit user session of the logged in user
    * @param requestSource  the request source
    */
  def checkAdditionalPermission(characteristic: String)(implicit userSession: UserSession, requestSource: RequestSource): Unit = {
    logger.debug(s"Will do permission check now: ${SimplifierServerSettings.activatePluginPermissionCheck}")
    if (SimplifierServerSettings.activatePluginPermissionCheck) {
      checkPermission(characteristic)
    }
  }

  protected def loadPermissions()(implicit userSession: UserSession, requestSource: RequestSource): Seq[GrantedPermission] = {
    val getPermissionsResult = Await.result(PluginPermissionObject.loadPermissions(JsonStorePluginPermission.technicalName, appServerDispatcher),
      pluginSettings.timeoutDuration)
    getPermissionsResult.permissionObjects
  }
}

object PermissionHandler {

  def apply(appServerDispatcher: AppServerDispatcher, pluginSettings: PluginSettings): PermissionHandler =
    new PermissionHandler(appServerDispatcher, pluginSettings)

  val MissingPermissionException: OperationFailureMessage =
    OperationFailureMessage("Insufficient permissions", ERROR_CODE_MISSING_PERMISSION, StatusCodes.Forbidden)
}
