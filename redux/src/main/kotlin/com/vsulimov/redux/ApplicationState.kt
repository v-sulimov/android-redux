package com.vsulimov.redux

/**
 * ApplicationState is an interface that represents the state of the application.
 * It is a marker interface, meaning that it does not have any methods or properties.
 * Classes that implement this interface are used to represent the state of the application
 * at a particular point in time.
 *
 * This interface is typically used in conjunction with a Redux store,
 * where the state of the application is managed by the store and updated
 * in response to actions dispatched by the application.
 *
 * Example:
 * ```
 * data class CounterState(val count: Int = 0) : ApplicationState
 * ```
 */
interface ApplicationState
