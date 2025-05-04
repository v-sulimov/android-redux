package com.vsulimov.redux.util

import android.util.Log
import com.vsulimov.redux.Action
import com.vsulimov.redux.Middleware
import com.vsulimov.redux.Reducer

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
     * @param S The type of the state managed by the middleware.
     */
    internal fun <S> logMiddlewareChangeAction(middleware: Middleware<S>, incomingAction: Action, outgoingAction: Action) {
        Log.d(TAG, "Middleware ${middleware::class.java.simpleName} changed action from ${incomingAction::class.java.simpleName} to ${outgoingAction::class.java.simpleName}")
    }

    /**
     * Logs when a middleware dispatches an additional action.
     *
     * @param middleware The middleware that dispatched the additional action.
     * @param dispatchedAction The additional action that was dispatched.
     * @param S The type of the state managed by the middleware.
     */
    internal fun <S> logMiddlewareDispatchAdditionalAction(middleware: Middleware<S>, dispatchedAction: Action) {
        Log.d(TAG, "Middleware ${middleware::class.java.simpleName} dispatched additional action ${dispatchedAction::class.java.simpleName}")
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
     * @param S The type of the state managed by the middleware.
     */
    internal fun <S> logAddMiddleware(middleware: Middleware<S>) {
        Log.d(TAG, "Middleware added ${middleware::class.java.simpleName}.")
    }

    /**
     * Logs the removal of the specified middleware.
     *
     * @param middleware The middleware being removed.
     * @param S The type of the state managed by the middleware.
     */
    internal fun <S> logRemoveMiddleware(middleware: Middleware<S>) {
        Log.d(TAG, "Middleware removed ${middleware::class.java.simpleName}.")
    }

    /**
     * Logs the current list of middlewares.
     *
     * @param middlewares The list of currently registered middlewares.
     * @param S The type of the state managed by the middlewares.
     */
    internal fun <S> logCurrentMiddlewares(middlewares: List<Middleware<S>>) {
        Log.d(TAG, "Current middlewares: ${middlewares.map { it::class.java.simpleName }}")
    }

    /**
     * Logs the addition of the specified reducer.
     *
     * @param reducer The reducer being added.
     * @param S The type of the state managed by the reducer.
     */
    internal fun <S> logAddReducer(reducer: Reducer<S>) {
        Log.d(TAG, "Reducer added ${reducer::class.java.simpleName}.")
    }

    /**
     * Logs the removal of the specified reducer.
     *
     * @param reducer The reducer being removed.
     * @param S The type of the state managed by the reducer.
     */
    internal fun <S> logRemoveReducer(reducer: Reducer<S>) {
        Log.d(TAG, "Reducer removed ${reducer::class.java.simpleName}.")
    }

    /**
     * Logs the current list of reducers.
     *
     * @param reducers The list of currently registered reducers.
     * @param S The type of the state managed by the reducers.
     */
    internal fun <S> logCurrentReducers(reducers: List<Reducer<S>>) {
        Log.d(TAG, "Current reducers: ${reducers.map { it::class.java.simpleName }}")
    }
}
