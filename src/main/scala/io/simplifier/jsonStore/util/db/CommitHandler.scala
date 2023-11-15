package io.simplifier.jsonStore.util.db

/**
  * Handler trait encapsulating a commit handling method.
  */
trait CommitHandler {

  /**
    * Execute handler after a successful commit
    */
  def handleConnectionCommit(): Unit

}





