package io.simplifier.jsonStore.util.db

import com.typesafe.config.Config
import io.simplifier.pluginbase.util.logging.Logging
import org.apache.commons.dbcp2.BasicDataSource
import org.squeryl.adapters.{H2Adapter, OracleAdapter}
import org.squeryl.internals.DatabaseAdapter
import org.squeryl.{Session, SessionFactory}

import java.sql.Connection
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import scala.collection.JavaConverters._

/**
 * Initializer for Database.
 * @author Arno Haase
 */
object SquerylInit extends Logging {

  val VendorNameOracle = "oracle"
  val VendorNameMySql  = "mysql"
  val VendorNameH2Mem = "h2-mem"
  val VendorNameH2 = "h2"

  @volatile private var ds: BasicDataSource = _
  @volatile private var vendor: String = _
  @volatile private var _tablePrefix: Option[String] = None
  @volatile private var _config: DbConfig = _

  case class DbConfig (host: String, port: String, database: String, connSpec: ConnectionSpec, username: String, password: String, poolSize: Int, tablePrefix: Option[String] = None)
  case class ConnectionSpec (driverClass: String, urlTemplate: String, dbAdapter: DatabaseAdapter)

  val mySqlConnParams: Seq[String] = Seq("allowPublicKeyRetrieval=true", "useUnicode=yes", "characterEncoding=UTF-8", "useFastDateParsing=false", "useSSL=false")
  val connParams: Map[String, Seq[String]] = Map(VendorNameMySql -> mySqlConnParams)

  def H2File(path: String): ConnectionSpec = ConnectionSpec ("org.h2.Driver", s"jdbc:h2:$path", getAdapter(VendorNameH2))
  val H2Mem: ConnectionSpec = ConnectionSpec ("org.h2.Driver", "jdbc:h2:mem:", getAdapter(VendorNameH2Mem))
  val MySql: ConnectionSpec = ConnectionSpec ("com.mysql.jdbc.Driver", "jdbc:mysql://{{host}}:{{port}}/{{database}}", getAdapter(VendorNameMySql))
  val Oracle: ConnectionSpec = ConnectionSpec ("oracle.jdbc.driver.OracleDriver", "jdbc:oracle:thin:@{{host}}:{{port}}/{{database}}", getAdapter(VendorNameOracle))

  def getAdapter(dbms: String): DatabaseAdapter = {
    dbms match {
      case VendorNameH2Mem => new H2Adapter
      case VendorNameH2 => new H2Adapter
      case VendorNameOracle => new OracleAdapter
      case VendorNameMySql => new MySQLInnoDBAdapterLongBlob
      case _ => throw new IllegalArgumentException (s"unknown dbms vendor $dbms")
    }
  }

  // TODO remove this weak security
  private[this] def hex2bytes(hex: String): Array[Byte] = {
    hex.replaceAll("[^0-9A-Fa-f]", "").sliding(2, 2).toArray.map(Integer.parseInt(_, 16).toByte)
  }

  private[this] val key = "F76C2E518DEDCD00751CB2DB10CA4CB3"

  private[this] def decrypt(str : String) : String = {
    val strbytes = hex2bytes(str)
    val cipher = Cipher.getInstance("AES")
    val secretKeySpec = new SecretKeySpec(hex2bytes(key), "AES")
    cipher.init(Cipher.DECRYPT_MODE, secretKeySpec)
    new String(cipher.doFinal(strbytes))
  }

  def parseConfig (config: Config, customAdapters: Map[String, DatabaseAdapter] = Map.empty,
                   connectionStringParameter: Map[String, Seq[String]] = Map.empty): DbConfig = {
    val dbRaw = config.getObject("database").unwrapped.asScala
    // TODO remove this weak security
    def dbPw = dbRaw.getOrElse("crypt", "plain") match {
      case "plain" => db("pass")
      case _ => decrypt(db("pass"))
    }

    def db(key: String) = dbRaw(key).toString

    def evaluateConnectionStringParams(vendor: String, connectionSpec: ConnectionSpec): ConnectionSpec = {
      (connParams.get(vendor), connectionStringParameter.get(vendor)) match {
        case (None, None) => connectionSpec
        case (defaultParams, additionalParams) =>
          val allParams = defaultParams.getOrElse(Seq()) ++ additionalParams.getOrElse(Seq())
          if(allParams.nonEmpty) {
            val newUrlTemplate = connectionSpec.urlTemplate + allParams.mkString("?", "&", "")
            connectionSpec.copy(urlTemplate = newUrlTemplate)
          } else {
            connectionSpec
          }
      }
    }

    val poolSize = dbRaw.get("pool-size") match {
      case Some (i: Integer) => i.intValue
      case Some (_) => throw new IllegalArgumentException ("pool-size must be an int value, if configured")
      case None => 20
    }

    val tp = dbRaw.get("table_prefix").map (_.asInstanceOf[String])

    vendor = db("dbms")
    val parsedConfig = vendor match {
      case VendorNameH2     => H2FileConfig (db("database"), poolSize, tp)
      case VendorNameH2Mem  => H2MemConfig (poolSize, tp)
      case VendorNameMySql  => val connectionSpec = evaluateConnectionStringParams(VendorNameMySql, MySql)
        DbConfig (db("host"), db("port"), db("database"), connectionSpec, db("user"), dbPw, poolSize, tp)
      case VendorNameOracle => val connectionSpec = evaluateConnectionStringParams(VendorNameOracle, Oracle)
        DbConfig (db("host"), db("port"), db("database"), connectionSpec, db("user"), dbPw, poolSize, tp)
      case _ => throw new IllegalArgumentException (s"unknown dbms vendor $vendor")
    }

    val parsedConfigWithAdapter = customAdapters.get(parsedConfig.connSpec.driverClass) match {
      case None => parsedConfig
      case Some(customAdapter) => parsedConfig.copy(connSpec = parsedConfig.connSpec.copy(dbAdapter = customAdapter)) // Override adapter
    }
    parsedConfigWithAdapter
  }

  def H2FileConfig (path: String, poolSize: Int = 10, tablePrefix: Option[String] = None): DbConfig = DbConfig("", "", "", H2File(path), "sa", "", poolSize, tablePrefix)
  def H2MemConfig (poolSize: Int = 10, tablePrefix: Option[String] = None): DbConfig = DbConfig ("", "", "", H2Mem, "sa", "", poolSize, tablePrefix)

  def dataSource: BasicDataSource = ds
  def vendorName: String = vendor
  def isInitialized: Boolean = vendor != null

  def config: DbConfig = _config

  /**
   * @return a string to be prefixed to all database tables. NB: This prefix is not (yet) evaluated in all deployment
   *         units, and the appServer in particular currently ignores it.
   */
  def tablePrefix: Option[String] = _tablePrefix

  def initWith (config: DbConfig): Unit = {
    synchronized {
      if (ds != null && ! ds.isClosed) ds.close()

      _tablePrefix = config.tablePrefix
      _config = config

      val jdbcUrl = config.connSpec.urlTemplate
        .replace ("{{host}}", config.host)
        .replace ("{{port}}", config.port)
        .replace ("{{database}}", config.database)

      ds = new BasicDataSource
      ds.setDefaultAutoCommit (false)
      ds.setDefaultReadOnly (false)
      ds.setInitialSize (2)
      ds.setMaxTotal (config.poolSize)

      ds.setDriverClassName (config.connSpec.driverClass)
      ds.setUrl (jdbcUrl)
      ds.setUsername (config.username)
      ds.setPassword (config.password)

      SessionFactory.concreteFactory = Some (() => {
        val connection = new CommitHandlerConnectionWrapper(ds.getConnection())
        val result = Session.create(connection, config.connSpec.dbAdapter)
        result.setLogger((sql: String) => logger.trace(sql))
        result
      })
    }
  }

  /**
   * Do not use.
   * Needed for handling large amounts of data in Post CD Plugin
   *
   * @return database connection
   */
  def getRawConnection: Connection = {
    SessionFactory.newSession.connection
  }

  Runtime.getRuntime.addShutdownHook(new Thread() {
    override def run(): Unit = {
      if (ds != null && ! ds.isClosed) ds.close()
    }
  })
}
