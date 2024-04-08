package db.migration.mysql

class V1_1__Migrate_To_Database extends db.migration.common.V1_1__Migrate_To_Database {
  override val insertStatement: String = s"INSERT INTO ${tablePrefix}Json_Store_Value (id, key_text, data, collection_id) VALUES (?, ?, ?, ?)"
  override val colInsertStatement: String = s"INSERT INTO ${tablePrefix}Json_Store_Collection (id, name) VALUES (?, ?)"
}
