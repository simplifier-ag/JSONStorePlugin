package io.simplifier.jsonStore.util.db

/**
 * Trait to indicate entities which contain a name value (or definition).
 */
trait NamedEntity {

  def name: String

}