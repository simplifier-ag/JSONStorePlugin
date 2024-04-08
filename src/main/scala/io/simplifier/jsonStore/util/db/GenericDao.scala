package io.simplifier.jsonStore.util.db

import io.simplifier.pluginbase.util.logging.Logging
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.dsl.ast._
import org.squeryl.{KeyedEntity, Table}

import scala.reflect.ClassTag

/**
 * Generic base class for DAOs (Data Access Objects).
 *
 * @tparam A entity type
 * @tparam K key type
 */
abstract class GenericDao[A <: KeyedEntity[K], K] extends Logging with QueryHeadOptionFix {

  // oracle restriction max 1000 values for in condition
  final val MAX_IN_CONDITION_VALUES = 1000

  /** Table of entity type. */
  def table: Table[A]

  def enableQueryLog(): Unit = {
    org.squeryl.Session.currentSession.setLogger(statement => logger.info(statement))
  }

  /**
   * Insert entity.
   */
  @throws[Throwable]
  def insert(entity: A): A = inTransaction {
    table.insert(entity)
  }

  def bulkInsert(entity: A): A = table.insert(entity)

  /**
   * Insert entity, or update it if it already exists.
   */
  def insertOrUpdate(entity: A): A = inTransaction {
    table.insertOrUpdate(entity)
  }

  /**
   * Insert multiple entities at once
   * @param batch Seq[A] entity list
   */
  def batchInsert(batch: Seq[A]): Unit = inTransaction {
    table.insert(batch)
  }

  //TODO: THIS DOES NOT WORK WITH COMPOSITE KEYS!!!
  /**
   * Insert entity, if it is not already existing.
   *
   * @param entity entity to insert
   *
   * @return Left with already existing entity, or Right with inserted entity
   */
  def insertIfNotExisting(entity: A): Either[A, A] = inTransaction {
    table.lookup(entity.id) match {
      case Some(existing) => Left(existing)
      case None => Right(table.insert(entity))
    }
  }

  //TODO: THIS DOES NOT WORK WITH COMPOSITE KEYS!!!
  /**
   * Insert entity, if it is not already existing.
   *
   * @param entity             entity to insert
   * @param whereClauseFunctor condition for selection
   *
   * @return Left with already existing entity, or Right with inserted entity
   */
  def insertIfNotExisting(entity: A, whereClauseFunctor: A => LogicalBoolean): Either[A, A] = inTransaction {
    table.where(whereClauseFunctor).headOptionFixed match {
      case Some(existing) => Left(existing)
      case None => Right(table.insert(entity))
    }
  }

  /**
   * Get entity by its primary key.
   */
  def getById(id: K): Option[A] = inTransaction {
    table.lookup(id)
  }


  /**
   * Return all ids.
   *
   * @return the ids as a vector.
   */
  def getAllIds: Vector[K] = inTransaction {
    from(table)(t => where(1 === 1) select t.id).toVector
  }


  /**
   * Return all ids by matching condition.
   *
   * @param whereClauseFunctor condition for selection.
   *
   * @return found ids or an empty Vector, or None
   */
  protected def getAllIdsBy(whereClauseFunctor: A => LogicalBoolean): Vector[K] = inTransaction {
    table.where(whereClauseFunctor).map(_.id).toVector
  }


  /**
   * Get all entities.
   */
  //noinspection AccessorLikeMethodIsEmptyParen
  def getAll(): Vector[A] = inTransaction {
    table.toVector
  }

  /**
   * Update a single entity.
   */
  @throws[Throwable]
  def update(entity: A): A = inTransaction {
    table.update(entity)
    entity
  }


  def bulkUpdate(entity: A): A = {
    table.update(entity)
    entity
  }

  /**
   * Updates values for all entries identified by the where clause
   *
   * @param whereClause       the where clause identifying all entries
   * @param updateAssignments the update assignments
   *
   * @return
   */
  def update(whereClause: A => LogicalBoolean, updateAssignments: A => Seq[UpdateAssignment]): Int = inTransaction {
    table.update(a => where(whereClause(a))
      set (updateAssignments(a): _*))
  }

  /**
   * Delete an entity by its primary key.
   */
  def delete(id: K): Unit = inTransaction {
    table.delete(id)
  }

  def bulkDelete(id: K): Unit = table.delete(id)

  /**
   * Delete an entity.
   */
  def delete(entity: A): Unit = inTransaction {
    table.delete(entity.id)
  }

  def bulkDelete(entity: A): Unit = table.delete(entity.id)

  /**
   * Delete all entities.
   */
  def truncate(): Unit = inTransaction {
    table.deleteWhere(_ => 1 === 1)
  }


  /**
   * Get first entity matching condition.
   *
   * @param whereClauseFunctor condition for selection
   *
   * @return found entity, or None
   */
  protected def getBy(whereClauseFunctor: A => LogicalBoolean): Option[A] = inTransaction {
    table.where(whereClauseFunctor).headOptionFixed
  }

  /**
   * Get all entities matching a condition.
   *
   * @param whereClauseFunctor condition for selection
   *
   * @return all found entities
   */
  protected def getAllBy(whereClauseFunctor: A => LogicalBoolean): Vector[A] = inTransaction {
    table.where(whereClauseFunctor).toVector
  }

  /**
   * Delete all entities matching condition.
   *
   * @param whereClauseFunctor condition for deletion
   */
  protected def deleteBy(whereClauseFunctor: A => LogicalBoolean): Unit = inTransaction {
    table.deleteWhere(whereClauseFunctor)
  }

  /**
   * Get named entity by name.
   *
   * @param name name ot search for
   */
  def getByName(name: String)(implicit ev: A <:< NamedEntity): Option[A] = getBy(_.name === name)

  /**
   * Get all named entities with a specific name
   *
   * @param name name to search for
   */
  def getAllByName(name: String)(implicit ev: A <:< NamedEntity): Vector[A] = getAllBy(_.name === name)

  /**
   * Get all items sorted by name (case insensitive)
   */
  def getAllSortedByName()(implicit ev: A <:< NamedEntity): Vector[A] = inTransaction {
    from(table)(e => select(e) orderBy lower(e.name)).toVector
  }

  /**
   * Get all names, sorted by name (case insensitive)
   */
  def getAllNames()(implicit ev: A <:< NamedEntity): Seq[String] = inTransaction {
    from(table)(e => select(e.name) orderBy lower(e.name)).toVector
  }

  /**
   * Get a map of all names (value) by their id (key).
   */
  def getAllNamesById()(implicit ev: A <:< NamedEntity): Map[K, String] = inTransaction {
    from(table)(e => select(e.id, e.name)).toMap
  }

  /**
   * Partitions entities in batches of 1000 due to ORACLE's WHERE IN limit of 1.000.
   *
   * @param entities   the entities to partition.
   *
   * @return           the vector of entities partitioned in batches of 1.000.
   */
  def partitionEntities(entities: TraversableOnce[A]): Vector[Vector[A]] = entities.toVector.sliding(MAX_IN_CONDITION_VALUES, MAX_IN_CONDITION_VALUES).toVector


  /**
   * Partitions elements e.g. ids in batches of 1000 due to ORACLE's WHERE IN limit of 1.000.
   *
   * @param elements   the elements [[T]] to partition.
   *
   * @return           the vector of elements partitioned in batches of 1.000.
   */
  def partitionElements[T: ClassTag](elements: TraversableOnce[T]): Vector[Vector[T]] = elements.toVector.sliding(MAX_IN_CONDITION_VALUES, MAX_IN_CONDITION_VALUES).toVector



  /**
   * alias for 'insert'
   */
  @throws[Throwable]
  def create(entity: A): A = insert(entity)

  def bulkCreate(entity: A): A = bulkInsert(entity)

  //noinspection AccessorLikeMethodIsEmptyParen
  def getCount(): Int = inTransaction {
    from(table)(_ => compute(count)).toInt
  }
}
