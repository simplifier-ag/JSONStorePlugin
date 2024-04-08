package io.simplifier.jsonStore.util.db

import org.squeryl.Schema
import org.squeryl.adapters.MySQLInnoDBAdapter
import org.squeryl.internals.StatementWriter

/**
  * Created by p005 on 31.08.2016.
  */
class MySQLInnoDBAdapterLongBlob extends MySQLInnoDBAdapter {

  override def binaryTypeDeclaration = "longblob"

  override def writeCreateTable[T](t: org.squeryl.Table[T], sw: StatementWriter, schema: Schema) = {
    super.writeCreateTable(t, sw, schema)
    sw.write(" ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin")
  }

}