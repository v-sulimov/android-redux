package com.vsulimov.redux

import android.util.Log
import com.vsulimov.redux.exception.CalledFromWrongThreadException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The central hub of the Redux library, managing application state, reducers, and middlewares.
 *
 * The store:
 * - Holds the single source of truth for the application state.
 * - Processes actions through middlewares and reducers to update the state.
 * - Provides a reactive state flow for observing state in the UI.
 *
 * ### Type Parameters
 * @param S The type of the state (e.g., a data class like `CounterState`).
 *
 * ### Parameters
 * @param initialState The initial state of the application, treated as immutable.
 * @param isMainThread A function to check if the current thread is the main thread. Defaults to always true.
 *
 * ### Thread Safety
 * All methods that modify the store or dispatch actions (e.g., `dispatch`, `addReducer`, `removeReducer`,
 * `addMiddleware`, `removeMiddleware`, `dispatchToMiddlewares`, `dispatchToReducers`) must be called
 * from the main thread, as specified by the `isMainThread` function. Calling these methods from other
 * threads will result in an `CalledFromWrongThreadException`.
 *
 * ### Best Practices
 * - Use immutable state objects to prevent accidental mutations.
 * - Add reducers and middlewares before dispatching actions for predictable behavior.
 * - Observe state changes via [stateFlow] in the UI.
 */
class Store<S>(
    initialState: S,
    private val isMainThread: () -> Boolean = { true }
) {

    private var state: S = initialState
    private val _stateFlow = MutableStateFlow(initialState)
    private val middlewares = mutableListOf<Middleware<S>>()
    private val reducers = mutableListOf<Reducer<S>>()
    private val actionQueue = mutableListOf<Action>()
    private var isProcessing = false

    /**
     * A reactive flow of state updates, suitable for observing in UI.
     *
     * This flow is read-only and can be collected using `collect()` in UI.
     * It emits the latest state whenever it changes after an action is processed.
     */
    val stateFlow: StateFlow<S> = _stateFlow.asStateFlow()

    private fun checkMainThread() {
        if (!isMainThread()) {
            throw CalledFromWrongThreadException("This method must be called from the main thread")
        }
    }

    /**
     * Dispatches an action through the middleware chain and then to all reducers to update the state.
     *
     * This is the primary method for initiating state changes:
     * 1. The action is added to a queue.
     * 2. If no action is currently being processed, the queue is processed sequentially.
     * 3. Each action passes through all middlewares in order, then to all reducers.
     * 4. The new state is emitted via [stateFlow] after each action is fully processed.
     *
     * Note: Actions dispatched during processing are queued and handled after the current action completes.
     *
     * @param action The action to dispatch.
     */
    fun dispatch(action: Action) {
        logActionDispatch(action)
        checkMainThread()
        actionQueue.add(action)
        if (!isProcessing) {
            isProcessing = true
            while (actionQueue.isNotEmpty()) {
                val currentAction = actionQueue.removeAt(0)
                val dispatchFunction =
                    middlewares.foldRight({ resultAction: Action -> dispatchToReducers(resultAction) }) { middleware, next ->
                        { incomingAction: Action ->
                            val wrappedNext = { outgoingAction: Action ->
                                if (outgoingAction != incomingAction) {
                                    logMiddlewareChangeAction(middleware, incomingAction, outgoingAction)
                                }
                                next(outgoingAction)
                            }
                            val wrappedDispatch = { dispatchedAction: Action ->
                                logMiddlewareDispatchAdditionalAction(middleware, action)
                                dispatch(dispatchedAction)
                            }
                            middleware.invoke(incomingAction, state, wrappedNext, wrappedDispatch)
                        }
                    }
                dispatchFunction(currentAction)
            }
            isProcessing = false
        }
    }

    /**
     * Dispatches an action only through the middleware chain, bypassing reducers.
     *
     * Useful for:
     * - Testing middleware behavior in isolation.
     * - Triggering side effects without altering the state.
     *
     * @param action The action to process.
     */
    fun dispatchToMiddlewares(action: Action) {
        logActionDispatchToMiddlewares(action)
        checkMainThread()
        val dispatchFunction =
            middlewares.foldRight({ _: Action -> /* No-op */ })
            { middleware, next ->
                { transformedAction: Action ->
                    middleware.invoke(transformedAction, state, next) { dispatchedAction ->
                        dispatch(dispatchedAction)
                    }
                }
            }
        dispatchFunction(action)
    }

    /**
     * Dispatches an action directly to all reducers, bypassing middlewares.
     *
     * Useful for:
     * - Testing reducers in isolation.
     * - Applying state changes without middleware interference.
     *
     * @param action The action to process.
     */
    fun dispatchToReducers(action: Action) {
        logActionDispatchToReducers(action)
        checkMainThread()
        var newState = state
        reducers.forEach { reducer ->
            newState = reducer.reduce(action, newState)
        }
        state = newState
        _stateFlow.value = state
    }

    /**
     * Adds a reducer to the store, enabling it to process future actions.
     *
     * Reducers are applied in the order they are added. Duplicate reducers are allowed and will be applied multiple times.
     *
     * @param reducer The reducer to add.
     */
    fun addReducer(reducer: Reducer<S>) {
        logAddReducer(reducer)
        checkMainThread()
        reducers.add(reducer)
        logCurrentReducers()
    }

    /**
     * Removes a reducer from the store, preventing it from processing future actions.
     *
     * @param reducer The reducer to remove.
     */
    fun removeReducer(reducer: Reducer<S>) {
        logRemoveReducer(reducer)
        checkMainThread()
        reducers.remove(reducer)
        logCurrentReducers()
    }

    /**
     * Adds a middleware to the store, enabling it to intercept future actions.
     *
     * Middlewares are applied in the order they are added. Duplicate middlewares are allowed and will be invoked multiple times.
     *
     * @param middleware The middleware to add.
     */
    fun addMiddleware(middleware: Middleware<S>) {
        logAddMiddleware(middleware)
        checkMainThread()
        middlewares.add(middleware)
        logCurrentMiddlewares()
    }

    /**
     * Removes a middleware from the store, preventing it from intercepting future actions.
     *
     * @param middleware The middleware to remove.
     */
    fun removeMiddleware(middleware: Middleware<S>) {
        logRemoveMiddleware(middleware)
        checkMainThread()
        middlewares.remove(middleware)
        logCurrentMiddlewares()
    }

    /**
     * Retrieves the current state of the store.
     *
     * This method is thread-safe and returns a snapshot of the state at the time of the call.
     * Treat the returned state as immutable to avoid unintended side effects.
     *
     * @return The current state of the application.
     */
    fun getState(): S = state

    /**
     * Logs the dispatching of the specified action.
     *
     * @param action The action being dispatched.
     */
    private fun logActionDispatch(action: Action) {
        Log.d(TAG, "Dispatching action ${action::class.java.simpleName}.")
    }

    /**
     * Logs when a middleware changes an action from one type to another.
     *
     * @param middleware The middleware that changed the action.
     * @param incomingAction The original action before the change.
     * @param outgoingAction The action after being changed by the middleware.
     */
    private fun logMiddlewareChangeAction(middleware: Middleware<S>, incomingAction: Action, outgoingAction: Action) {
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
    private fun logMiddlewareDispatchAdditionalAction(middleware: Middleware<S>, dispatchedAction: Action) {
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
    private fun logActionDispatchToMiddlewares(action: Action) {
        Log.d(TAG, "Dispatching action ${action::class.java.simpleName} to middlewares only.")
    }

    /**
     * Logs that reducers have received the specified action.
     *
     * @param action The action received by reducers.
     */
    private fun logActionDispatchToReducers(action: Action) {
        Log.d(TAG, "Reducers received action ${action::class.java.simpleName}.")
    }

    /**
     * Logs the addition of the specified middleware.
     *
     * @param middleware The middleware being added.
     */
    private fun logAddMiddleware(middleware: Middleware<S>) {
        Log.d(TAG, "Middleware added ${middleware::class.java.simpleName}.")
    }

    /**
     * Logs the removal of the specified middleware.
     *
     * @param middleware The middleware being removed.
     */
    private fun logRemoveMiddleware(middleware: Middleware<S>) {
        Log.d(TAG, "Middleware removed ${middleware::class.java.simpleName}.")
    }

    /**
     * Logs the current list of middlewares.
     */
    private fun logCurrentMiddlewares() {
        Log.d(TAG, "Current middlewares: ${middlewares.map { it::class.java.simpleName }}")
    }

    /**
     * Logs the addition of the specified reducer.
     *
     * @param reducer The reducer being added.
     */
    private fun logAddReducer(reducer: Reducer<S>) {
        Log.d(TAG, "Reducer added ${reducer::class.java.simpleName}.")
    }

    /**
     * Logs the removal of the specified reducer.
     *
     * @param reducer The reducer being removed.
     */
    private fun logRemoveReducer(reducer: Reducer<S>) {
        Log.d(TAG, "Reducer removed ${reducer::class.java.simpleName}.")
    }

    /**
     * Logs the current list of reducers.
     */
    private fun logCurrentReducers() {
        Log.d(TAG, "Current reducers: ${reducers.map { it::class.java.simpleName }}")
    }

    companion object {

        private const val TAG = "Store"
    }
}
