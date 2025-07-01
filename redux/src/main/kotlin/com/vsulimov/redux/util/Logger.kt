package com.vsulimov.redux.util

import android.util.Log
import com.vsulimov.redux.Action
import com.vsulimov.redux.Middleware
import com.vsulimov.redux.Reducer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive

/**
 * A utility object for logging Redux-related events in an Android application.
 *
 * The [Logger] object provides methods to log actions, middleware operations, and reducer changes
 * in a Redux state management system. It uses Android's [Log] utility with a consistent tag
 * for debugging purposes. All logging is performed at the DEBUG level.
 *
 * This logger is designed to be used internally within a Redux store to track the flow of actions,
 * middleware modifications, and state changes, aiding in debugging and monitoring of the application state.
 */
object Logger {

    /** The tag used for all log messages, set to "Store". */
    private const val TAG = "Store"

    /**
     * Logs the dispatching of an action to the Redux store.
     *
     * @param action The [Action] being dispatched.
     */
    internal fun logActionDispatch(action: Action) {
        Log.d(TAG, "Dispatching action ${action::class.java.simpleName}.")
    }

    /**
     * Logs when a middleware changes an incoming action to an outgoing action.
     *
     * @param middleware The [Middleware] that processed the action.
     * @param incomingAction The original [Action] received by the middleware.
     * @param outgoingAction The modified [Action] produced by the middleware.
     */
    internal fun <S> logMiddlewareChangeAction(
        middleware: Middleware<S>,
        incomingAction: Action,
        outgoingAction: Action
    ) {
        Log.d(
            TAG,
            "Middleware ${middleware::class.java.simpleName} changed action from ${incomingAction::class.java.simpleName} to ${outgoingAction::class.java.simpleName}"
        )
    }

    /**
     * Logs when a middleware dispatches an additional action.
     *
     * @param middleware The [Middleware] that dispatched the additional action.
     * @param dispatchedAction The additional [Action] dispatched by the middleware.
     */
    internal fun <S> logMiddlewareDispatchAdditionalAction(middleware: Middleware<S>, dispatchedAction: Action) {
        Log.d(
            TAG,
            "Middleware ${middleware::class.java.simpleName} dispatched additional action ${dispatchedAction::class.java.simpleName}"
        )
    }

    /**
     * Logs the dispatching of an action to middlewares only.
     *
     * This is typically used when an action is processed by middlewares but not forwarded to reducers.
     *
     * @param action The [Action] being dispatched to middlewares.
     */
    internal fun logActionDispatchToMiddlewares(action: Action) {
        Log.d(TAG, "Dispatching action ${action::class.java.simpleName} to middlewares only.")
    }

    /**
     * Logs when an action is received by reducers.
     *
     * @param action The [Action] received by the reducers.
     */
    internal fun logActionDispatchToReducers(action: Action) {
        Log.d(TAG, "Reducers received action ${action::class.java.simpleName}.")
    }

    /**
     * Logs the addition of a middleware to the Redux store.
     *
     * @param middleware The [Middleware] added to the store.
     */
    internal fun <S> logAddMiddleware(middleware: Middleware<S>) {
        Log.d(TAG, "Middleware added ${middleware::class.java.simpleName}.")
    }

    /**
     * Logs the removal of a middleware from the Redux store.
     *
     * @param middleware The [Middleware] removed from the store.
     */
    internal fun <S> logRemoveMiddleware(middleware: Middleware<S>) {
        Log.d(TAG, "Middleware removed ${middleware::class.java.simpleName}.")
    }

    /**
     * Logs the current list of middlewares registered in the Redux store.
     *
     * @param middlewares The list of [Middleware] currently registered.
     */
    internal fun <S> logCurrentMiddlewares(middlewares: List<Middleware<S>>) {
        Log.d(TAG, "Current middlewares: ${middlewares.map { it::class.java.simpleName }}")
    }

    /**
     * Logs the current middleware coroutine scopes and their activity status.
     *
     * @param scopes A map of [Middleware] to their associated [CoroutineScope], indicating whether each scope is active.
     */
    internal fun <S> logCurrentMiddlewareScopes(scopes: Map<Middleware<S>, CoroutineScope>) {
        Log.d(
            TAG,
            "Current middleware scopes: ${
                scopes.map {
                    "${it.key::class.java.simpleName} -> ${if (it.value.isActive) "active" else "inactive"}"
                }
            }"
        )
    }

    /**
     * Logs the addition of a reducer to the Redux store.
     *
     * @param reducer The [Reducer] added to the store.
     */
    internal fun <S> logAddReducer(reducer: Reducer<S>) {
        Log.d(TAG, "Reducer added ${reducer::class.java.simpleName}.")
    }

    /**
     * Logs the removal of a reducer from the Redux store.
     *
     * @param reducer The [Reducer] removed from the store.
     */
    internal fun <S> logRemoveReducer(reducer: Reducer<S>) {
        Log.d(TAG, "Reducer removed ${reducer::class.java.simpleName}.")
    }

    /**
     * Logs the current list of reducers registered in the Redux store.
     *
     * @param reducers The list of [Reducer] currently registered.
     */
    internal fun <S> logCurrentReducers(reducers: List<Reducer<S>>) {
        Log.d(TAG, "Current reducers: ${reducers.map { it::class.java.simpleName }}")
    }
}
