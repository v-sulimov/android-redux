package com.vsulimov.redux

/**
 * Next is being used for [Middleware] chaining.
 */
typealias Next<State> = (Action, State) -> Action

/**
 * Middleware provides a third-party extension point between dispatching an action,
 * and the moment it reaches the reducer. People use Redux middleware for logging, crash reporting,
 * talking to an asynchronous API, routing, and more.
 */
interface Middleware<State> {

    /**
     * Handle incoming action and returns the resulting action.
     * You should always return the resulting action using next() function call.
     */
    fun handleAction(action: Action, state: State, next: Next<State>): Action
}
