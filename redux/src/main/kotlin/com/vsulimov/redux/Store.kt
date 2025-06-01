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
import java.util.LinkedList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A central component in the Redux architecture for managing application state, reducers, and middlewares.
 *
 * The `Store` class acts as the single source of truth for the application's state, coordinating the flow of
 * actions through a chain of middlewares and reducers to produce predictable state updates. It leverages Kotlin
 * coroutines and flows for reactive state observation and asynchronous middleware operations, while enforcing
 * thread safety and immutability best practices.
 *
 * ### Core Features
 * - **State Management**: Maintains a single, immutable state object, updated solely via reducers in response to actions.
 * - **Action Processing**: Processes actions sequentially using an internal queue, ensuring consistency by passing
 *   them through middlewares and then reducers.
 * - **Reactive Updates**: Provides a [StateFlow] for real-time state observation, ideal for reactive UI updates.
 * - **Middleware Scopes**: Assigns each middleware a [CoroutineScope] for asynchronous tasks, automatically cleaned up
 *   upon middleware removal.
 *
 * ### Type Parameters
 * @param S The type of the application state, which must extend [ApplicationState]. It is **strongly recommended**
 *   to use immutable types (e.g., Kotlin `data class` with `copy()` for updates) to prevent unintended mutations.
 *
 * ### Parameters
 * @param initialState The initial state of the application, treated as immutable. This value is immediately emitted
 *   through [stateFlow] upon store creation.
 * @param isMainThread A lambda to determine if the current thread is the main thread (defaults to `{ true }`).
 *   In Android, configure this to enforce main-thread access, e.g., `Looper.myLooper() == Looper.getMainLooper()`.
 *
 * ### Thread Safety
 * Methods that modify the store or dispatch actions—[dispatch], [addReducer], [removeReducer], [addMiddleware],
 * [removeMiddleware], [addMiddlewareWithTag], [removeMiddlewaresByTag], [dispatchToMiddlewares], and
 * [dispatchToReducers]—must be called from the main thread (enforced via [checkMainThread]). Violations throw
 * [CalledFromWrongThreadException]. However, [getState] and [stateFlow] are thread-safe and accessible from any thread.
 *
 * ### Best Practices
 * - **Immutability**: Use immutable state objects to ensure predictable behavior and avoid side effects outside the
 *   Redux flow.
 * - **Initialization**: Add reducers and middlewares before dispatching actions for consistent processing.
 * - **Reactive UI**: Observe [stateFlow] in UI components for seamless state-driven updates.
 * - **Exception Handling**: Middlewares and reducers must handle their own exceptions, as the store propagates them
 *   without catching to expose issues clearly.
 * - **Async Operations**: Use middleware-provided [CoroutineScope]s for asynchronous tasks, which are auto-cancelled
 *   on removal to prevent leaks.
 *
 * ### Usage Example
 * ```kotlin
 * data class CounterState(val count: Int = 0) : ApplicationState
 * val store = Store(CounterState()) { Looper.myLooper() == Looper.getMainLooper() }
 * val reducer: Reducer<CounterState> = { action, state ->
 *     when (action) {
 *         is Increment -> state.copy(count = state.count + 1)
 *         else -> state
 *     }
 * }
 * store.addReducer(reducer)
 * store.dispatch(Increment())
 * println(store.getState().count) // Outputs: 1
 * ```
 */
class Store<S : ApplicationState>(initialState: S, private val isMainThread: () -> Boolean = { true }) {

    private var state: S = initialState
    private val _stateFlow = MutableStateFlow(initialState)
    private val middlewares = mutableListOf<Middleware<S>>()
    private val middlewareScopes = mutableMapOf<Middleware<S>, CoroutineScope>()
    private val taggedMiddlewares = mutableMapOf<String, MutableList<Middleware<S>>>()
    private val reducers = mutableListOf<Reducer<S>>()
    private val actionQueue = LinkedList<Action>()
    private var isProcessing = false

    /**
     * Provides a reactive, read-only view of the application state.
     *
     * This [StateFlow] emits the current state whenever it changes after an action is processed via [dispatch] or
     * [dispatchToReducers]. It’s ideal for UI components to reactively update based on state changes.
     *
     * ### Thread Safety
     * Safe to collect from any thread.
     *
     * ### Best Practice
     * Treat emitted states as immutable to maintain Redux integrity.
     *
     * ### Usage Example
     * ```kotlin
     * coroutineScope.launch {
     *     store.stateFlow.collect { state ->
     *         println("Current count: ${state.count}")
     *     }
     * }
     * ```
     */
    val stateFlow: StateFlow<S> = _stateFlow.asStateFlow()

    /**
     * Verifies that the current thread is the main thread, throwing an exception otherwise.
     *
     * This internal helper enforces thread safety for store-modifying operations, using the [isMainThread] lambda.
     * Called by methods like [dispatch], [addReducer], and [addMiddleware].
     *
     * @throws CalledFromWrongThreadException If the current thread is not the main thread.
     */
    private fun checkMainThread() {
        if (!isMainThread()) {
            throw CalledFromWrongThreadException("This method must be called from the main thread")
        }
    }

    /**
     * Dispatches an action to be processed sequentially through middlewares and reducers, updating the state.
     *
     * The primary method for initiating state changes, it ensures actions are handled in order:
     * 1. Queued in an internal [LinkedList].
     * 2. Processed one-by-one when no other action is active (`isProcessing` is `false`).
     * 3. Passed through all middlewares in addition order.
     * 4. Final action sent to all reducers to compute the new state.
     * 5. New state emitted via [stateFlow].
     *
     * ### Behavior
     * - **Sequential Processing**: Queues actions during processing, handling them in order.
     * - **Exceptions**: Propagates uncaught exceptions from middlewares or reducers for explicit error handling.
     * - **Logging**: Logs dispatch and middleware transformations via [logActionDispatch] and related utilities.
     *
     * @param action The action to dispatch, typically an object or data class representing a state change intent.
     *
     * @throws CalledFromWrongThreadException If called from a non-main thread.
     */
    fun dispatch(action: Action) {
        logActionDispatch(action)
        checkMainThread()
        actionQueue.add(action)
        if (!isProcessing) {
            isProcessing = true
            while (actionQueue.isNotEmpty()) {
                val currentAction = actionQueue.removeFirst()
                val dispatchFunction =
                    middlewares.foldRight({ resultAction: Action ->
                        dispatchToReducers(resultAction)
                    }) { middleware, next ->
                        { incomingAction: Action ->
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
                dispatchFunction(currentAction)
            }
            isProcessing = false
        }
    }

    /**
     * Processes an action through the middleware chain only, without updating state or invoking reducers.
     *
     * Useful for triggering side effects (e.g., logging, API calls) or testing middleware behavior in isolation.
     *
     * ### Behavior
     * - Passes the action through middlewares in addition order.
     * - Middlewares may transform the action or dispatch additional actions.
     * - Does not affect state or invoke reducers.
     * - Propagates uncaught middleware exceptions.
     *
     * @param action The action to process through middlewares.
     *
     * @throws CalledFromWrongThreadException If called from a non-main thread.
     */
    fun dispatchToMiddlewares(action: Action) {
        logActionDispatchToMiddlewares(action)
        checkMainThread()
        val dispatchFunction =
            middlewares.foldRight({ _: Action -> }) { middleware, next ->
                { transformedAction: Action ->
                    val scope = middlewareScopes[middleware]
                        ?: throw IllegalStateException("No scope found for middleware $middleware")
                    middleware.invoke(transformedAction, state, next, { dispatchedAction ->
                        dispatch(dispatchedAction)
                    }, scope)
                }
            }
        dispatchFunction(action)
    }

    /**
     * Applies an action directly to all reducers, bypassing middlewares, and updates the state.
     *
     * Ideal for testing reducers or applying state changes without middleware interference.
     *
     * ### Behavior
     * - Passes the action to reducers in addition order.
     * - Updates state and emits it via [stateFlow].
     * - Propagates uncaught reducer exceptions.
     *
     * @param action The action to process through reducers.
     *
     * @throws CalledFromWrongThreadException If called from a non-main thread.
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
     * Registers a reducer to process future dispatched actions.
     *
     * Reducers are pure functions that compute new states based on the current state and an action.
     *
     * ### Behavior
     * - Adds the reducer to the end of the list, applied in order during [dispatch] or [dispatchToReducers].
     * - Allows duplicates, executing them multiple times if added repeatedly.
     * - Logs addition via [logAddReducer] and [logCurrentReducers].
     *
     * @param reducer The reducer to add, implementing [Reducer].
     *
     * @throws CalledFromWrongThreadException If called from a non-main thread.
     */
    fun addReducer(reducer: Reducer<S>) {
        logAddReducer(reducer)
        checkMainThread()
        reducers.add(reducer)
        logCurrentReducers(reducers)
    }

    /**
     * Removes the first occurrence of a reducer, preventing it from processing future actions.
     *
     * ### Behavior
     * - Removes only the first instance if duplicates exist.
     * - Logs removal via [logRemoveReducer] and [logCurrentReducers].
     *
     * @param reducer The reducer to remove.
     *
     * @throws CalledFromWrongThreadException If called from a non-main thread.
     */
    fun removeReducer(reducer: Reducer<S>) {
        logRemoveReducer(reducer)
        checkMainThread()
        reducers.remove(reducer)
        logCurrentReducers(reducers)
    }

    /**
     * Registers a middleware to intercept and process future dispatched actions.
     *
     * Middlewares handle side effects, action transformations, or additional dispatches, executing in addition order.
     *
     * ### Behavior
     * - Adds the middleware with a new [CoroutineScope] (using [SupervisorJob] and [Dispatchers.Main]).
     * - Allows duplicates, invoking them multiple times if added repeatedly.
     * - Logs addition via [logAddMiddleware], [logCurrentMiddlewares], and [logCurrentMiddlewareScopes].
     *
     * @param middleware The middleware to add, implementing [Middleware].
     *
     * @throws CalledFromWrongThreadException If called from a non-main thread.
     */
    fun addMiddleware(middleware: Middleware<S>) {
        logAddMiddleware(middleware)
        checkMainThread()
        middlewares.add(middleware)
        middlewareScopes[middleware] = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        logCurrentMiddlewares(middlewares)
        logCurrentMiddlewareScopes(middlewareScopes)
    }

    /**
     * Registers a middleware with a tag for grouped management, such as bulk removal.
     *
     * Extends [addMiddleware] by associating a tag, allowing multiple middlewares to share the same tag.
     *
     * ### Behavior
     * - Adds the middleware to [middlewares] and [taggedMiddlewares] under the specified tag.
     * - Assigns a [CoroutineScope] for async tasks, cancelled on removal.
     * - Logs addition via [logAddMiddleware], [logCurrentMiddlewares], and [logCurrentMiddlewareScopes].
     *
     * @param middleware The middleware to add, implementing [Middleware].
     * @param tag A case-sensitive identifier for grouping middlewares (e.g., "logging").
     *
     * ### Usage Example
     * ```kotlin
     * val loggingMiddleware: Middleware<CounterState> = { action, _, next, _, _ ->
     *     println("Action dispatched: $action")
     *     next(action)
     * }
     * store.addMiddlewareWithTag(loggingMiddleware, "logging")
     * ```
     *
     * @throws CalledFromWrongThreadException If called from a non-main thread.
     */
    fun addMiddlewareWithTag(middleware: Middleware<S>, tag: String) {
        checkMainThread()
        middlewares.add(middleware)
        val list = taggedMiddlewares.getOrPut(tag) { mutableListOf() }
        list.add(middleware)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        middlewareScopes[middleware] = scope
        logAddMiddleware(middleware)
        logCurrentMiddlewares(middlewares)
        logCurrentMiddlewareScopes(middlewareScopes)
    }

    /**
     * Checks if any middlewares are associated with the given tag.
     *
     * Useful for verifying tagged middleware presence before operations like removal.
     *
     * ### Behavior
     * - Returns `true` if the tag exists in [taggedMiddlewares] with a non-empty list, `false` otherwise.
     *
     * @param tag The case-sensitive tag to check.
     * @return `true` if middlewares exist for the tag, `false` otherwise.
     *
     * @throws CalledFromWrongThreadException If called from a non-main thread.
     */
    fun hasMiddlewaresForTag(tag: String): Boolean {
        checkMainThread()
        return taggedMiddlewares[tag]?.isNotEmpty() ?: false
    }

    /**
     * Removes the first occurrence of a middleware and cancels its coroutine scope.
     *
     * ### Behavior
     * - Removes the middleware from [middlewares] and its scope from [middlewareScopes].
     * - Cancels ongoing async tasks in the scope.
     * - Logs removal via [logRemoveMiddleware], [logCurrentMiddlewares], and [logCurrentMiddlewareScopes].
     *
     * @param middleware The middleware to remove.
     *
     * @throws CalledFromWrongThreadException If called from a non-main thread.
     */
    fun removeMiddleware(middleware: Middleware<S>) {
        logRemoveMiddleware(middleware)
        checkMainThread()
        middlewares.remove(middleware)
        middlewareScopes[middleware]?.cancel()
        middlewareScopes.remove(middleware)
        logCurrentMiddlewares(middlewares)
        logCurrentMiddlewareScopes(middlewareScopes)
    }

    /**
     * Removes all middlewares associated with a tag, cleaning up their resources.
     *
     * ### Behavior
     * - Removes all tagged middlewares from [middlewares] and [taggedMiddlewares].
     * - Cancels their [CoroutineScope]s and removes them from [middlewareScopes].
     * - No-op if the tag doesn’t exist or has no middlewares.
     * - Logs removals via [logRemoveMiddleware], [logCurrentMiddlewares], and [logCurrentMiddlewareScopes].
     *
     * @param tag The case-sensitive tag identifying middlewares to remove.
     *
     * ### Usage Example
     * ```kotlin
     * store.addMiddlewareWithTag(loggingMiddleware, "logging")
     * store.removeMiddlewaresByTag("logging") // Removes all "logging" middlewares
     * ```
     *
     * @throws CalledFromWrongThreadException If called from a non-main thread.
     */
    fun removeMiddlewaresByTag(tag: String) {
        checkMainThread()
        val middlewaresToRemove = taggedMiddlewares[tag] ?: return
        for (middleware in middlewaresToRemove) {
            middlewares.remove(middleware)
            middlewareScopes[middleware]?.cancel()
            middlewareScopes.remove(middleware)
            logRemoveMiddleware(middleware)
        }
        taggedMiddlewares.remove(tag)
        logCurrentMiddlewares(middlewares)
        logCurrentMiddlewareScopes(middlewareScopes)
    }

    /**
     * Returns the current application state.
     *
     * Provides a thread-safe snapshot of the state. Treat the returned value as immutable.
     *
     * ### Usage Example
     * ```kotlin
     * val currentCount = store.getState().count
     * println("Current count: $currentCount")
     * ```
     *
     * @return The current state of type [S].
     */
    fun getState(): S = state
}
