package io.simplifier.jsonStore.util.db

import io.simplifier.pluginbase.util.logging.Logging
import org.squeryl.Schema

import scala.collection.immutable.SortedSet
import scala.collection.mutable
import scala.util.control.NonFatal

/**
 * Verification for database schema.
 * Created by Christian Simon on 26.10.16.
 */
object DatabaseVerification extends Logging {

  /**
   * Exception for a failed verification.
   *
   * @param msg   message
   * @param cause optional cause
   */
  class SchemaVerificationException(msg: String, cause: Throwable = null) extends Exception(msg, cause)

  /**
   * Verify database schema.
   *
   * @param schema squeryl schema to validate against
   * @throws SchemaVerificationException if the verification was not successful
   */
  @throws[SchemaVerificationException]
  def verifyDatabaseSchema(schema: Schema): Unit = {
    SquerylInit.vendorName match {
      case SquerylInit.VendorNameOracle => new OracleSchemaVerification(schema).verify()
      case SquerylInit.VendorNameMySql => new MySqlSchemaVerification(schema).verify()
      case other => logger.warn(s"No schema verification available for vendor $other")
    }
  }

  /**
   * Verification implementation for a specific database vendor.
   */
  trait DatabaseSchemaVerification {

    /** Squeryl schema to validate against */
    def schema: Schema

    /**
     * Verify database schema.
     *
     * @throws SchemaVerificationException if the verification was not successful
     */
    @throws[SchemaVerificationException]
    def verify(): Unit

    private lazy val fieldData = for {
      table <- schema.tables
      fmd <- table.posoMetaData.fieldsMetaData
    } yield {
      (table.name, fmd.columnName)
    }

    protected lazy val fieldMap: Map[String, Set[String]] =
      fieldData.groupBy(_._1).mapValues(_.map(_._2).to[SortedSet])

  }

  class OracleSchemaVerification(val schema: Schema) extends DatabaseSchemaVerification {

    override def verify(): Unit = {
      logger.debug("Verifying Oracle database schema ...")
      val connection = SquerylInit.dataSource.getConnection()
      try {

        val stmt = connection.prepareStatement(
          "SELECT column_name FROM all_tab_cols WHERE LOWER(table_name) = ? AND LOWER(owner) = ? AND HIDDEN_COLUMN = 'NO'")

        fieldMap foreach {
          case (tableName, fieldNames) =>
            logger.trace(s"Checking table $tableName")
            stmt.setString(1, tableName.toLowerCase)
            stmt.setString(2, SquerylInit.config.username.toLowerCase)
            val resultSet = stmt.executeQuery()
            val buffer = mutable.ArrayBuffer.empty[String]
            while (resultSet.next()) {
              buffer += resultSet.getString(1)
            }
            resultSet.close()
            val foundFieldNames: Set[String] = buffer.map((s: String) => s.toLowerCase).to[SortedSet]
            val expectedFieldNames: Set[String] = fieldNames.map(_.toLowerCase)

            if (foundFieldNames.isEmpty) {
              throw new SchemaVerificationException(s"Table ${tableName.toUpperCase} not found in schema")
            } else if (foundFieldNames != expectedFieldNames) {
              throw new SchemaVerificationException(s"Table ${tableName.toUpperCase} has unexpected columns: " +
                s"Expected = $expectedFieldNames, Actual = $foundFieldNames")
            }

        }
        logger.info("Schema verified successfully.")

      } catch {
        case sve: SchemaVerificationException => throw sve
        case NonFatal(e) => throw new SchemaVerificationException("Error in schema validation", e)
      } finally {
        connection.close()
      }
    }

  }

  class MySqlSchemaVerification(val schema: Schema) extends DatabaseSchemaVerification {

    override def verify(): Unit = {
      logger.debug("Verifying MySQL database schema ...")
      val connection = SquerylInit.dataSource.getConnection()
      try {

        fieldMap foreach {
          case (tableName, fieldNames) if tableName.matches("[a-zA-Z0-9_]+") =>
            logger.trace(s"Checking table $tableName")
            val stmt = connection.prepareStatement("EXPLAIN " + tableName)
            val resultSet = stmt.executeQuery()
            val buffer = mutable.ArrayBuffer.empty[String]
            while (resultSet.next()) {
              buffer += resultSet.getString(1)
            }
            resultSet.close()
            val foundFieldNames: Set[String] = buffer.map((s: String) => s.toLowerCase).to[SortedSet]
            val expectedFieldNames: Set[String] = fieldNames.map(_.toLowerCase)

            if (foundFieldNames.isEmpty) {
              throw new SchemaVerificationException(s"Table ${tableName.toUpperCase} not found in schema")
            } else if (foundFieldNames != expectedFieldNames) {
              throw new SchemaVerificationException(s"Table ${tableName.toUpperCase} has unexpected columns: " +
                s"Expected = $expectedFieldNames, Actual = $foundFieldNames")
            }
          case (invalidTableName, _) =>
            throw new SchemaVerificationException(s"Invalid table name $invalidTableName")
        }
        logger.info("Schema verified successfully.")

      } catch {
        case sve: SchemaVerificationException => throw sve
        case NonFatal(e) => throw new SchemaVerificationException("Error in schema validation", e)
      } finally {
        connection.close()
      }
    }

  }

}

