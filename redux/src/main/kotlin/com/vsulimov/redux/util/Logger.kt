package com.vsulimov.redux.util

import android.util.Log
import com.vsulimov.redux.Action
import com.vsulimov.redux.ApplicationState
import com.vsulimov.redux.Middleware
import com.vsulimov.redux.Reducer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive

/**
 * A utility object for logging various events related to actions, middlewares, and reducers in the application.
 * All log messages are tagged with a constant tag "Store" and are output at the debug level.
 */
object Logger {

    private const val TAG = "Store"

    /**
     * Logs the dispatching of the specified action.
     *
     * @param action The action being dispatched.
     */
    internal fun logActionDispatch(action: Action) {
        Log.d(TAG, "Dispatching action ${action::class.java.simpleName}.")
    }

    /**
     * Logs when a middleware changes an action from one type to another.
     *
     * @param middleware The middleware that changed the action.
     * @param incomingAction The original action before the change.
     * @param outgoingAction The action after being changed by the middleware.
     */
    internal fun <S : ApplicationState> logMiddlewareChangeAction(
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
     * @param middleware The middleware that dispatched the additional action.
     * @param dispatchedAction The additional action that was dispatched.
     */
    internal fun <S : ApplicationState> logMiddlewareDispatchAdditionalAction(
        middleware: Middleware<S>,
        dispatchedAction: Action
    ) {
        Log.d(
            TAG,
            "Middleware ${middleware::class.java.simpleName} dispatched additional action ${dispatchedAction::class.java.simpleName}"
        )
    }

    /**
     * Logs the dispatching of the specified action to middlewares.
     *
     * @param action The action being dispatched to middlewares.
     */
    internal fun logActionDispatchToMiddlewares(action: Action) {
        Log.d(TAG, "Dispatching action ${action::class.java.simpleName} to middlewares only.")
    }

    /**
     * Logs that reducers have received the specified action.
     *
     * @param action The action received by reducers.
     */
    internal fun logActionDispatchToReducers(action: Action) {
        Log.d(TAG, "Reducers received action ${action::class.java.simpleName}.")
    }

    /**
     * Logs the addition of the specified middleware.
     *
     * @param middleware The middleware being added.
     */
    internal fun <S : ApplicationState> logAddMiddleware(middleware: Middleware<S>) {
        Log.d(TAG, "Middleware added ${middleware::class.java.simpleName}.")
    }

    /**
     * Logs the removal of the specified middleware.
     *
     * @param middleware The middleware being removed.
     */
    internal fun <S : ApplicationState> logRemoveMiddleware(middleware: Middleware<S>) {
        Log.d(TAG, "Middleware removed ${middleware::class.java.simpleName}.")
    }

    /**
     * Logs the current list of middlewares.
     *
     * @param middlewares The list of currently registered middlewares.
     */
    internal fun <S : ApplicationState> logCurrentMiddlewares(middlewares: List<Middleware<S>>) {
        Log.d(TAG, "Current middlewares: ${middlewares.map { it::class.java.simpleName }}")
    }

    /**
     * Logs the current middleware scopes, indicating whether each scope is active or inactive.
     *
     * @param scopes A map of middlewares to their corresponding coroutine scopes.
     */
    internal fun <S : ApplicationState> logCurrentMiddlewareScopes(scopes: Map<Middleware<S>, CoroutineScope>) {
        Log.d(
            TAG,
            "Current middleware scopes: ${scopes.map {
                "${it.key::class.java.simpleName} -> ${if (it.value.isActive) "active" else "inactive"}"
            }}"
        )
    }

    /**
     * Logs the addition of the specified reducer.
     *
     * @param reducer The reducer being added.
     */
    internal fun <S : ApplicationState> logAddReducer(reducer: Reducer<S>) {
        Log.d(TAG, "Reducer added ${reducer::class.java.simpleName}.")
    }

    /**
     * Logs the removal of the specified reducer.
     *
     * @param reducer The reducer being removed.
     */
    internal fun <S : ApplicationState> logRemoveReducer(reducer: Reducer<S>) {
        Log.d(TAG, "Reducer removed ${reducer::class.java.simpleName}.")
    }

    /**
     * Logs the current list of reducers.
     *
     * @param reducers The list of currently registered reducers.
     */
    internal fun <S : ApplicationState> logCurrentReducers(reducers: List<Reducer<S>>) {
        Log.d(TAG, "Current reducers: ${reducers.map { it::class.java.simpleName }}")
    }
}
