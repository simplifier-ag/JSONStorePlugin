package io.simplifier.jsonStore.util.db

import org.squeryl.Query

import java.io.Closeable
import scala.language.implicitConversions

/**
 * Fix for Squeryl bug #254 (https://github.com/squeryl/squeryl/issues/254)
 * The headOption operation did not close the ResultSet correctly.
 *
 * When the bug is fixed, this trait can be removed and migrated to the new Squeryl version.
 */
trait QueryHeadOptionFix {

  implicit def fixHeadOption[R](query: Query[R]): QueryHeadOptionFix.QueryWithFixedHeadOption[R] = {
    new QueryHeadOptionFix.QueryWithFixedHeadOption[R](query)
  }


}

object QueryHeadOptionFix extends QueryHeadOptionFix {

  class QueryWithFixedHeadOption[R](query: Query[R]) {

    /**
     * Return headOption of Query, but also close the ResultSet and Statement.
     *
     * @return result of query.headOption
     */
    def headOptionFixed: Option[R] = {
      val i = query.iterator
      try {
        if (i.hasNext)
          Some(i.next)
        else
          None
      } finally {
        i match {
          case c: Closeable => c.close()
        }
      }
    }

  }

}
