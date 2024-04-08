package db.migration.common

import io.simplifier.jsonStore.JsonStorePlugin
import io.simplifier.jsonStore.util.data.Checksum
import io.simplifier.jsonStore.util.db.SquerylInit
import io.simplifier.pluginbase.util.logging.Logging
import org.flywaydb.core.api.migration.{BaseJavaMigration, Context}
import org.mapdb.{DBMaker, HTreeMap, Serializer}
import org.squeryl.PrimitiveTypeMode.inTransaction

import java.io.{ByteArrayInputStream, File}
import java.nio.charset.StandardCharsets
import scala.util.{Failure, Success, Try}

abstract class V1_1__Migrate_To_Database extends BaseJavaMigration with Logging {

  val colInsertStatement: String
  val insertStatement: String
  val tablePrefix: String = SquerylInit.tablePrefix.find(_.nonEmpty).getOrElse("")

  override def migrate(context: Context): Unit = inTransaction {
    var counter: Int = 0
    var size: Long = 0
    val maxSize: Long = 104857600
    val connection = context.getConnection
    val config = JsonStorePlugin.BASIC_STATE.config
    val mapDbFileName: String = Try(config.getString("plugin.filename")) match {
      case Success(fn) => fn
      case Failure(e) if config.hasPath("plugin.filename") => throw e
      case _ =>
        logger.warn("Could not access the mapDB filename from the configuration " +
          "for path: [plugin.filename], using the value: [/tmp/jsonStore] as a fallback.")
        "/tmp/jsonStore"
    }
    logger.info("setup")
    val mapDb = DBMaker.fileDB(new File(mapDbFileName))
      .compressionEnable()
      .fileMmapEnable()
      .checksumEnable()
      .snapshotEnable()
      .make()
    logger.info("made file")
    try {
      mapDb.getAll.forEach {
        case (collectionName, _) =>
          val id = encodeKey(collectionName)
          val prepCollectionInsertStatement = connection.prepareStatement(colInsertStatement)
          prepCollectionInsertStatement.setString(1, id)
          prepCollectionInsertStatement.setString(2, collectionName)
          try {
            prepCollectionInsertStatement.execute()
          } finally {
            prepCollectionInsertStatement.close()
          }
          val collectionDbStore: HTreeMap[String, Array[Byte]] = mapDb.hashMapCreate(collectionName)
            .keySerializer(Serializer.STRING)
            .valueSerializer(new Serializer.CompressionWrapper(Serializer.BYTE_ARRAY))
            .makeOrGet()
          collectionDbStore.keySet().forEach { key =>
            Try(collectionDbStore.get(key)) match {
              case Failure(exception) => logger.warn(s"value for key $key in collection $collectionName could not be read. ${exception.getMessage}")
              case Success(value) => counter += 1
                if (counter % 100 == 0 || size >= maxSize) {
                  logger.debug(s"Counter: $counter")
                  logger.debug(s"Size: $size")
                  size = value.length
                  connection.commit()
                }
                val valueStream = new ByteArrayInputStream(value)
                val prepInsertStatement = connection.prepareStatement(insertStatement)
                prepInsertStatement.setString(1, encodeKey(id, key))
                prepInsertStatement.setString(2, key)
                prepInsertStatement.setBlob(3, valueStream)
                prepInsertStatement.setString(4, id)
                try {
                  prepInsertStatement.execute()
                } finally {
                  prepInsertStatement.close()
                }
            }
          }
      }
    } finally {
      mapDb.close()
    }

  }

  private def encodeKey(key: String): String = {
    val item = key.getBytes(StandardCharsets.UTF_8)
    Checksum.checksumSHA256(item)
  }

  private def encodeKey(collectionId: String, key: String): String = {
    val item = (collectionId + key).getBytes(StandardCharsets.UTF_8)
    Checksum.checksumSHA256(item)
  }

}
