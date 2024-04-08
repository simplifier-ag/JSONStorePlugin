package io.simplifier.jsonStore.permission

import io.simplifier.pluginbase.permission.PluginPermissionObject
import io.simplifier.pluginbase.permission.PluginPermissionObjectCharacteristics.CheckboxCharacteristic

object JsonStorePluginPermission extends PluginPermissionObject {

  val characteristicAdministrate = "administrate"

  val characteristicView = "view"

  val characteristicEdit = "edit"

  /**
    * Name of the permission object.
    */
  override val name: String = "Json Store Plugin"
  /**
    * Technical Name of the permission object.
    */
  override val technicalName: String = PluginPermissionObject.getTechnicalName("jsonStore Plugin")
  /**
    * Description of the permission object.
    */
  override val description: String = "Plugin: Handle permissions for the Json Store"
  /**
    * Possible characteristics for the admin ui.
    */
  override val characteristics: Seq[CheckboxCharacteristic] = Seq(
    CheckboxCharacteristic(characteristicAdministrate, "Administrate", "Administrate the plugin"),
    CheckboxCharacteristic(characteristicView, "View", "View exiting json data"),
    CheckboxCharacteristic(characteristicEdit, "Edit", "Create, edit and delete json data")
  )
}
