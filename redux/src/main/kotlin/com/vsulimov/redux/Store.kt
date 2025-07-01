package com.vsulimov.redux

import com.vsulimov.redux.exception.CalledFromWrongThreadException
import com.vsulimov.redux.util.Logger.logActionDispatch
import com.vsulimov.redux.util.Logger.logActionDispatchToMiddlewares
import com.vsulimov.redux.util.Logger.logActionDispatchToReducers
import com.vsulimov.redux.util.Logger.logAddMiddleware
import com.vsulimov.redux.util.Logger.logAddReducer
import com.vsulimov.redux.util.Logger.logCurrentMiddlewareScopes
import com.vsulimov.redux.util.Logger.logCurrentMiddlewares
import com.vsulimov.redux.util.Logger.logCurrentReducers
import com.vsulimov.redux.util.Logger.logMiddlewareChangeAction
import com.vsulimov.redux.util.Logger.logMiddlewareDispatchAdditionalAction
import com.vsulimov.redux.util.Logger.logRemoveMiddleware
import com.vsulimov.redux.util.Logger.logRemoveReducer
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A Redux store managing state, actions, middlewares, and reducers in a thread-safe manner.
 *
 * The [Store] holds the current state of type [S], manages a list of [Middleware] and [Reducer]
 * instances, and provides a [StateFlow] for observing state changes. It ensures thread safety by
 * requiring operations to be performed on the main thread (configurable via [isMainThread]) and
 * supports asynchronous middleware operations using [CoroutineScope]. Actions are processed in a
 * queue to ensure sequential execution, and the store logs key events using [Logger].
 *
 * @param S The type of the state managed by the store.
 * @param initialState The initial state of the store.
 * @param isMainThread A lambda to determine if the current thread is the main thread (defaults to always true).
 * @constructor Creates a [Store] with the given [initialState] and [isMainThread] check.
 * @throws IllegalStateException if a middleware is invoked without an associated [CoroutineScope].
 * @throws CalledFromWrongThreadException if a method is called from a non-main thread when [isMainThread] returns false.
 */
class Store<S>(initialState: S, private val isMainThread: () -> Boolean = { true }) {
    /**
     * The current state of the store.
     */
    private var state: S = initialState

    /**
     * A [MutableStateFlow] holding the current state, used to emit updates to subscribers.
     */
    private val _stateFlow = MutableStateFlow(initialState)

    /**
     * A list of middlewares that process actions before they reach reducers.
     */
    private val middlewares = mutableListOf<Middleware<S>>()

    /**
     * A map of middlewares to their associated [CoroutineScope] instances for asynchronous operations.
     */
    private val middlewareScopes = mutableMapOf<Middleware<S>, CoroutineScope>()

    /**
     * A map of tags to lists of middlewares, allowing grouped middleware management.
     */
    private val taggedMiddlewares = mutableMapOf<String, MutableList<Middleware<S>>>()

    /**
     * A list of reducers that update the state based on actions.
     */
    private val reducers = mutableListOf<Reducer<S>>()

    /**
     * A queue of actions to ensure sequential processing.
     */
    private val actionQueue = LinkedList<Action>()

    /**
     * A flag indicating whether the store is currently processing actions.
     */
    private var isProcessing = false

    /**
     * A [StateFlow] for observing the store's state changes.
     */
    val stateFlow: StateFlow<S> = _stateFlow.asStateFlow()

    /**
     * Checks if the current thread is the main thread, throwing an exception if not.
     *
     * @throws CalledFromWrongThreadException if [isMainThread] returns false.
     */
    private fun checkMainThread() {
        if (!isMainThread()) throw CalledFromWrongThreadException("This method must be called from the main thread")
    }

    /**
     * Builds the middleware chain with a final step, enhancing readability and reusability.
     *
     * @param finalStep The final function to call after all middlewares have processed the action.
     * @return A function that processes an action through the middleware chain.
     */
    private fun buildMiddlewareChain(finalStep: (Action) -> Unit): (Action) -> Unit =
        middlewares.foldRight(finalStep) { middleware, next ->
            { incomingAction ->
                val wrappedNext = { outgoingAction: Action ->
                    if (outgoingAction != incomingAction) {
                        logMiddlewareChangeAction(middleware, incomingAction, outgoingAction)
                    }
                    next(outgoingAction)
                }
                val wrappedDispatch = { dispatchedAction: Action ->
                    logMiddlewareDispatchAdditionalAction(middleware, dispatchedAction)
                    dispatch(dispatchedAction)
                }
                val scope = middlewareScopes[middleware]
                    ?: throw IllegalStateException("No scope found for middleware $middleware")
                middleware.invoke(incomingAction, state, wrappedNext, wrappedDispatch, scope)
            }
        }

    /**
     * Processes a single action through middlewares and reducers.
     *
     * @param action The [Action] to process.
     */
    private fun processAction(action: Action) {
        val dispatchFunction = buildMiddlewareChain { resultAction -> dispatchToReducers(resultAction) }
        dispatchFunction(action)
    }

    /**
     * Dispatches an action to the store for processing by middlewares and reducers.
     *
     * The action is added to a queue to ensure sequential processing. Middlewares process the action
     * in order, potentially modifying it or dispatching additional actions. The final action is then
     * passed to reducers to update the state. Logs are generated via [Logger] for key events.
     *
     * @param action The [Action] to dispatch.
     * @throws CalledFromWrongThreadException if called from a non-main thread.
     * @throws IllegalStateException if a middleware lacks an associated [CoroutineScope].
     */
    fun dispatch(action: Action) {
        logActionDispatch(action)
        checkMainThread()
        actionQueue.add(action)
        if (!isProcessing) {
            isProcessing = true
            while (actionQueue.isNotEmpty()) {
                processAction(actionQueue.removeFirst())
            }
            isProcessing = false
        }
    }

    /**
     * Dispatches an action to middlewares only, bypassing reducers.
     *
     * This allows middlewares to process the action (e.g., for side effects) without updating the state.
     * Logs are generated via [Logger] for the dispatch event.
     *
     * @param action The [Action] to dispatch to middlewares.
     * @throws CalledFromWrongThreadException if called from a non-main thread.
     * @throws IllegalStateException if a middleware lacks an associated [CoroutineScope].
     */
    fun dispatchToMiddlewares(action: Action) {
        logActionDispatchToMiddlewares(action)
        checkMainThread()
        val dispatchFunction = buildMiddlewareChain { /* No-op */ }
        dispatchFunction(action)
    }

    /**
     * Dispatches an action directly to reducers, bypassing middlewares.
     *
     * The action is processed by all registered reducers to compute a new state, which is then
     * updated in the store and emitted via [stateFlow]. Logs are generated via [Logger] for the dispatch event.
     *
     * @param action The [Action] to dispatch to reducers.
     * @throws CalledFromWrongThreadException if called from a non-main thread.
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
     * Adds a reducer to the store.
     *
     * The reducer is appended to the list of reducers and will process future actions dispatched via
     * [dispatch] or [dispatchToReducers]. Logs are generated via [Logger] for the addition and current reducers.
     *
     * @param reducer The [Reducer] to add.
     * @throws CalledFromWrongThreadException if called from a non-main thread.
     */
    fun addReducer(reducer: Reducer<S>) {
        logAddReducer(reducer)
        checkMainThread()
        reducers.add(reducer)
        logCurrentReducers(reducers)
    }

    /**
     * Removes a reducer from the store.
     *
     * The reducer is removed from the list of reducers and will no longer process actions. Logs are
     * generated via [Logger] for the removal and current reducers.
     *
     * @param reducer The [Reducer] to remove.
     * @throws CalledFromWrongThreadException if called from a non-main thread.
     */
    fun removeReducer(reducer: Reducer<S>) {
        logRemoveReducer(reducer)
        checkMainThread()
        reducers.remove(reducer)
        logCurrentReducers(reducers)
    }

    /**
     * Adds a middleware to the store.
     *
     * The middleware is appended to the list of middlewares and assigned a [CoroutineScope] for
     * asynchronous operations. It will process future actions dispatched via [dispatch] or
     * [dispatchToMiddlewares]. Logs are generated via [Logger] for the addition, current middlewares,
     * and middleware scopes.
     *
     * @param middleware The [Middleware] to add.
     * @throws CalledFromWrongThreadException if called from a non-main thread.
     */
    fun addMiddleware(middleware: Middleware<S>) {
        checkMainThread()
        addMiddlewareInternal(middleware)
    }

    /**
     * Adds a middleware to the store with an associated tag.
     *
     * The middleware is added to the list of middlewares and associated with the specified [tag] for
     * grouped management. It is also assigned a [CoroutineScope] for asynchronous operations. Logs
     * are generated via [Logger] for the addition, current middlewares, and middleware scopes.
     *
     * @param middleware The [Middleware] to add.
     * @param tag A string tag to group the middleware for later retrieval or removal.
     * @throws CalledFromWrongThreadException if called from a non-main thread.
     */
    fun addMiddlewareWithTag(middleware: Middleware<S>, tag: String) {
        checkMainThread()
        addMiddlewareInternal(middleware)
        taggedMiddlewares.getOrPut(tag) { mutableListOf() }.add(middleware)
    }

    /**
     * Checks if there are middlewares associated with the specified tag.
     *
     * @param tag The tag to check for associated middlewares.
     * @return True if there are middlewares for the tag, false otherwise.
     * @throws CalledFromWrongThreadException if called from a non-main thread.
     */
    fun hasMiddlewaresForTag(tag: String): Boolean {
        checkMainThread()
        return taggedMiddlewares[tag]?.isNotEmpty() == true
    }

    /**
     * Removes a middleware from the store.
     *
     * The middleware is removed from the list of middlewares, its [CoroutineScope] is canceled, and
     * it is removed from the scope map. Logs are generated via [Logger] for the removal, current
     * middlewares, and middleware scopes.
     *
     * @param middleware The [Middleware] to remove.
     * @throws CalledFromWrongThreadException if called from a non-main thread.
     */
    fun removeMiddleware(middleware: Middleware<S>) {
        checkMainThread()
        removeMiddlewareInternal(middleware)
    }

    /**
     * Removes all middlewares associated with the specified tag.
     *
     * All middlewares with the given [tag] are removed from the store, their [CoroutineScope]s are
     * canceled, and they are removed from the scope map. The tag is then removed from the
     * [taggedMiddlewares] map. Logs are generated via [Logger] for each removal, current middlewares,
     * and middleware scopes.
     *
     * @param tag The tag identifying the middlewares to remove.
     * @throws CalledFromWrongThreadException if called from a non-main thread.
     */
    fun removeMiddlewaresByTag(tag: String) {
        checkMainThread()
        val middlewaresToRemove = taggedMiddlewares[tag] ?: return
        middlewaresToRemove.forEach { removeMiddlewareInternal(it) }
        taggedMiddlewares.remove(tag)
    }

    /**
     * Retrieves the current state of the store.
     *
     * @return The current state of type [S].
     */
    fun getState(): S = state

    /**
     * Adds a middleware with its coroutine scope.
     *
     * @param middleware The [Middleware] to add.
     */
    private fun addMiddlewareInternal(middleware: Middleware<S>) {
        middlewares.add(middleware)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        middlewareScopes[middleware] = scope
        logAddMiddleware(middleware)
        logCurrentMiddlewares(middlewares)
        logCurrentMiddlewareScopes(middlewareScopes)
    }

    /**
     * Removes a middleware and cleans up its scope.
     *
     * @param middleware The [Middleware] to remove.
     */
    private fun removeMiddlewareInternal(middleware: Middleware<S>) {
        middlewares.remove(middleware)
        middlewareScopes[middleware]?.cancel()
        middlewareScopes.remove(middleware)
        logRemoveMiddleware(middleware)
        logCurrentMiddlewares(middlewares)
        logCurrentMiddlewareScopes(middlewareScopes)
    }
}
