package com.vsulimov.redux.exception

/**
 * An exception thrown when an operation is called from an incorrect thread.
 * It indicates that a method was invoked on a thread that does not meet the expected threading requirements.
 *
 * @param message A descriptive message explaining why the exception was thrown.
 * @constructor Creates a new instance of [CalledFromWrongThreadException] with the specified message.
 */
class CalledFromWrongThreadException(override val message: String) : Exception(message)
