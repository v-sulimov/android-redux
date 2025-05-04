package com.vsulimov.redux.exception

/**
 * Signals that a method has been invoked from a wrong thread.
 * In other words.
 * Some methods have rules on the thread from which they should be called.
 * This exception indicates that this rule has been violated.
 */
class CalledFromWrongThreadException(override val message: String) : Exception(message)
