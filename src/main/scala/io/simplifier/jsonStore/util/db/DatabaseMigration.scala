package io.simplifier.jsonStore.util.db

import com.typesafe.config.Config
import io.simplifier.pluginbase.util.logging.Logging
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.ClassicConfiguration
import org.flywaydb.core.api.{Location, MigrationVersion}
import org.squeryl.Schema

import scala.collection.JavaConverters._

/**
 * Flyway Migration tool for database schemas.
 *
 * @author Christian Simon
 * @param settings database settings
 * @param schema   squeryl schema to use for validation
 */
class DatabaseMigration(settings: Config, schema: Schema) extends Logging {


  import DatabaseMigration._
  import io.simplifier.pluginbase.util.config.ConfigExtension._

  def runMigration(): Unit = {
    val enabled: Boolean = settings.get(CFG_ENABLED, false)
    val baseline: Option[String] = settings.getOpt(CFG_BASELINE)
    val tablePrefix: Option[String] = SquerylInit.tablePrefix
    val schemaVersionTable: Option[String] = settings.getOpt(CFG_SCHEMA_VERSION_TABLE) orElse (tablePrefix map (_ + "schema_version"))
    val ignoreDirtySchema: Boolean = settings.get(CFG_IGNORE_DIRTY_SCHEMA, false)
    val verifySchema: Boolean = enabled && settings.get[Boolean](CFG_VERIFY_SCHEMA, false)
    val lockRetryCount: Int = settings.get[Int](CFG_LOCK_RETRY_COUNT, 600)

    if (! SquerylInit.isInitialized) {
      logger.error("Cannot perform database update: DB session is not configured.")
    } else if (enabled) {
      logger.info("Performing automatic database upgrade" + baseline.fold("")(bl => s" (with baseline: $bl)"))
      performUpdate(baseline, schemaVersionTable, ignoreDirtySchema, tablePrefix, verifySchema: Boolean, lockRetryCount)
    } else {
      logger.debug("Automatic database upgrade disabled")
    }
  }

  /**
   * Perform migration
   *
   * @param baseline           schema baseline (only migrations after this baseline will be run); this implies ignoreDirtySchema
   * @param schemaVersionTable override for table where schema version is stored
   * @param ignoreDirtySchema  ignore dirty schema (schemas without version table, but not empty)
   * @param tablePrefix        prefix for all table names (only if supported)
   * @param verifySchema       flag if the table names and field names are to be validated against the schema definition
   */
  private def performUpdate(baseline: Option[String], schemaVersionTable: Option[String], ignoreDirtySchema: Boolean,
                            tablePrefix: Option[String], verifySchema: Boolean, lockRetryCount: Int): Unit = {
    val configuration = new ClassicConfiguration()
    configuration.setDataSource(SquerylInit.dataSource)
    configuration.setValidateOnMigrate(false)
    configuration.setLocations(new Location("db/migration/" + SquerylInit.vendorName))
    // retry for connection if table is locked by other cluster member migration (default 600 seconds)
    configuration.setLockRetryCount(lockRetryCount)

    baseline.foreach { base =>
      configuration.setBaselineVersion(MigrationVersion.fromVersion(base))
      configuration.setBaselineOnMigrate(true)
    }

    if (baseline.isEmpty && ignoreDirtySchema) {
      configuration.setBaselineVersion(MigrationVersion.fromVersion("0.0"))
      configuration.setBaselineOnMigrate(true)
    }

    if(schemaVersionTable.isDefined) {
      configuration.setTable(schemaVersionTable.get)
    } else {
      // Flyway renamed their schema_version table in a newer version,
      // but for consistency we want to use the old default name (some Scala Migrations use it!)
      configuration.setTable(tablePrefix.getOrElse("") + "schema_version")
    }

    configuration.setPlaceholders(Map("prefix" -> tablePrefix.getOrElse("")).asJava)

    val flyway = new Flyway(configuration)

    //Run database update.
    flyway.migrate()

    // Verify schema
    if (verifySchema && SquerylInit.isInitialized) {
      DatabaseVerification.verifyDatabaseSchema(schema)
    }

  }

}

object DatabaseMigration {

  private[DatabaseMigration] val CFG_DB_UPDATE = "database_update."

  private[DatabaseMigration] val CFG_ENABLED = CFG_DB_UPDATE + "automatic_update"
  private[DatabaseMigration] val CFG_BASELINE = CFG_DB_UPDATE + "baseline"
  private[DatabaseMigration] val CFG_SCHEMA_VERSION_TABLE = CFG_DB_UPDATE + "schema_version_table"
  private[DatabaseMigration] val CFG_IGNORE_DIRTY_SCHEMA = CFG_DB_UPDATE + "ignore_dirty_schema"
  private[DatabaseMigration] val CFG_VERIFY_SCHEMA = CFG_DB_UPDATE + "verify_schema"
  private[DatabaseMigration] val CFG_LOCK_RETRY_COUNT = "lock_retry_count"

  /**
   * Create database migration tool.
   *
   * @param settings configuration
   * @param schema   squeryl schema to use for validation
   *
   * @return migration tool
   */
  def apply(settings: Config, schema: Schema): DatabaseMigration = new DatabaseMigration(settings, schema)

}
