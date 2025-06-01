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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.LinkedList

/**
 * The central hub of the Redux library, responsible for managing the application's state, reducers, and middlewares.
 *
 * The `Store` class serves as the core component in a Redux-based architecture, providing a single source of truth
 * for the application state and orchestrating the flow of actions through middlewares and reducers to produce
 * predictable state updates. It leverages Kotlin's coroutines and flows to enable reactive state observation and
 * asynchronous middleware operations, while enforcing thread safety and immutability best practices.
 *
 * ### Key Responsibilities
 * - **State Management**: Maintains the application's state as a single, immutable object, updated only through
 *   reducers in response to dispatched actions.
 * - **Action Processing**: Queues and processes actions sequentially, passing them through middlewares and then
 *   reducers to compute the new state.
 * - **Reactive Updates**: Exposes the state via a [StateFlow], allowing UI components to observe and react to
 *   state changes in real-time.
 * - **Middleware Scopes**: Provides each middleware with a dedicated [CoroutineScope] for asynchronous operations,
 *   automatically cancelling these scopes when the middleware is removed.
 *
 * ### Type Parameters
 * @param S The type of the application state (e.g., a data class like `CounterState`). It is **strongly recommended**
 *   to use immutable state objects to prevent accidental mutations. For example, define your state as a `data class`
 *   and use its `copy()` function to create updated instances during reduction.
 *
 * ### Parameters
 * @param initialState The initial state of the application, treated as immutable. This value sets the starting point
 *   for the state and is emitted immediately through [stateFlow].
 * @param isMainThread A lambda function that checks whether the current thread is the main thread. Defaults to always
 *   returning `true`, suitable for single-threaded environments or testing. In Android applications, this should be
 *   configured to enforce main-thread-only access (e.g., using `Looper.myLooper() == Looper.getMainLooper()`).
 *
 * ### Thread Safety
 * All methods that modify the store or dispatch actions—such as [dispatch], [addReducer], [removeReducer],
 * [addMiddleware], [removeMiddleware], [addMiddlewareWithTag], [removeMiddlewaresByTag], [dispatchToMiddlewares],
 * and [dispatchToReducers]—must be called from the main thread, as determined by the [isMainThread] function.
 * Invoking these methods from a non-main thread will throw a [CalledFromWrongThreadException].
 *
 * ### Best Practices
 * - **Immutability**: Use immutable state objects (e.g., Kotlin `data class` with `copy()`) to ensure predictable
 *   behavior and prevent unintended side effects from state mutations outside the Redux flow.
 * - **Initialization**: Add reducers and middlewares before dispatching actions to ensure consistent and predictable
 *   processing from the outset.
 * - **Observation**: Subscribe to [stateFlow] in UI components to reactively update the interface as the state changes.
 * - **Exception Handling**: Middlewares and reducers are responsible for handling their own exceptions. The store does
 *   not catch exceptions, allowing them to propagate and surface potential issues rather than silently failing.
 * - **Asynchronous Operations**: Middlewares receive a [CoroutineScope] for launching coroutines, which is cancelled
 *   automatically when the middleware is removed, preventing resource leaks.
 */
class Store<S>(
    initialState: S,
    private val isMainThread: () -> Boolean = { true }
) {

    private var state: S = initialState
    private val _stateFlow = MutableStateFlow(initialState)
    private val middlewares = mutableListOf<Middleware<S>>()
    private val middlewareScopes = mutableMapOf<Middleware<S>, CoroutineScope>()
    private val taggedMiddlewares = mutableMapOf<String, MutableList<Middleware<S>>>()
    private val reducers = mutableListOf<Reducer<S>>()
    private val actionQueue = LinkedList<Action>()
    private var isProcessing = false

    /**
     * A reactive flow providing read-only access to the application's state.
     *
     * This [StateFlow] emits the current state whenever it is updated after an action is fully processed through
     * middlewares and reducers. It is designed for use in UI layers (e.g., Android Activities, Fragments, or Composables)
     * to observe state changes reactively using `collect()` or similar mechanisms.
     *
     * ### Usage
     * ```kotlin
     * launch {
     *     store.stateFlow.collect { state ->
     *         // Update UI with new state
     *     }
     * }
     * ```
     *
     * ### Properties
     * - **Thread Safety**: Safe to collect from any thread.
     * - **Immutability**: Treat emitted state objects as immutable to avoid unintended side effects.
     */
    val stateFlow: StateFlow<S> = _stateFlow.asStateFlow()

    /**
     * Ensures that the current thread is the main thread, throwing an exception if not.
     *
     * This private helper method enforces thread safety for all store-modifying operations, relying on the
     * [isMainThread] lambda provided at construction. It is called internally by methods like [dispatch],
     * [addReducer], and [addMiddleware].
     *
     * @throws CalledFromWrongThreadException If the current thread is not the main thread.
     */
    private fun checkMainThread() {
        if (!isMainThread()) {
            throw CalledFromWrongThreadException("This method must be called from the main thread")
        }
    }

    /**
     * Dispatches an action to be processed through the middleware chain and then all reducers, updating the state.
     *
     * This is the primary entry point for initiating state changes in the Redux architecture. Actions are processed
     * in a sequential, queued manner to ensure consistency and predictability:
     * 1. The action is added to an internal [LinkedList] queue.
     * 2. If no action is currently being processed (`isProcessing` is `false`), the queue begins processing.
     * 3. Each action is dequeued and passed through all middlewares in the order they were added.
     * 4. The final action (potentially transformed by middlewares) is sent to all reducers to compute the new state.
     * 5. The updated state is emitted via [stateFlow] after processing completes.
     *
     * ### Behavior
     * - **Queueing**: If an action is dispatched while another is being processed, it is queued and handled
     *   sequentially after the current action completes.
     * - **Exception Handling**: Exceptions thrown by middlewares or reducers are not caught by the store and will
     *   propagate up the call stack, potentially crashing the application. It is the responsibility of each
     *   middleware and reducer to handle exceptions appropriately to maintain store integrity.
     * - **Logging**: Each dispatch is logged via [logActionDispatch], and middleware transformations or additional
     *   dispatches are logged separately for debugging purposes.
     *
     * ### Parameters
     * @param action The action to dispatch, typically a data class or object representing an intent to change state.
     *
     * ### Thread Safety
     * Must be called from the main thread, enforced by [checkMainThread].
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
                    middlewares.foldRight({ resultAction: Action -> dispatchToReducers(resultAction) }) { middleware, next ->
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
     * Dispatches an action through the middleware chain only, bypassing reducers and state updates.
     *
     * This method is useful for scenarios where side effects (e.g., API calls, logging) need to be triggered
     * without altering the application state, or for testing middleware behavior in isolation.
     *
     * ### Behavior
     * - The action is passed through all middlewares in the order they were added.
     * - Middlewares can transform the action or dispatch additional actions via the provided `dispatch` function.
     * - The state is not updated, and reducers are not invoked.
     * - Exceptions thrown by middlewares are not caught and will propagate up the call stack.
     *
     * ### Parameters
     * @param action The action to process through the middleware chain.
     *
     * ### Thread Safety
     * Must be called from the main thread, enforced by [checkMainThread].
     *
     * @throws CalledFromWrongThreadException If called from a non-main thread.
     */
    fun dispatchToMiddlewares(action: Action) {
        logActionDispatchToMiddlewares(action)
        checkMainThread()
        val dispatchFunction =
            middlewares.foldRight({ _: Action -> /* No-op */ })
            { middleware, next ->
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
     * Dispatches an action directly to all reducers, bypassing the middleware chain.
     *
     * This method is useful for testing reducers in isolation or applying state changes without middleware
     * interference. It updates the state directly and emits the new state via [stateFlow].
     *
     * ### Behavior
     * - The action is passed to all reducers in the order they were added.
     * - Each reducer computes a new state based on the current state and the action.
     * - Exceptions thrown by reducers are not caught and will propagate up the call stack.
     *
     * ### Parameters
     * @param action The action to process through the reducers.
     *
     * ### Thread Safety
     * Must be called from the main thread, enforced by [checkMainThread].
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
     * Adds a reducer to the store, enabling it to process future dispatched actions.
     *
     * Reducers define how the state changes in response to actions. They are pure functions that take the
     * current state and an action, returning a new state. This method appends the reducer to the list,
     * and it will be applied in the order of addition during [dispatch] or [dispatchToReducers].
     *
     * ### Behavior
     * - Duplicate reducers are allowed and will be executed multiple times if added more than once.
     * - The addition is logged via [logAddReducer] and [logCurrentReducers].
     *
     * ### Parameters
     * @param reducer The reducer to add, implementing the [Reducer] interface.
     *
     * ### Thread Safety
     * Must be called from the main thread, enforced by [checkMainThread].
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
     * Removes a reducer from the store, preventing it from processing future actions.
     *
     * This method removes the first occurrence of the specified reducer from the list. If the reducer
     * was added multiple times, only one instance is removed.
     *
     * ### Behavior
     * - The removal is logged via [logRemoveReducer] and [logCurrentReducers].
     *
     * ### Parameters
     * @param reducer The reducer to remove.
     *
     * ### Thread Safety
     * Must be called from the main thread, enforced by [checkMainThread].
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
     * Adds a middleware to the store, enabling it to intercept and process future dispatched actions.
     *
     * Middlewares can perform side effects, transform actions, or dispatch additional actions. They are
     * executed in the order they are added during [dispatch] or [dispatchToMiddlewares]. Each middleware
     * is provided with a [CoroutineScope] for asynchronous operations, managed by the store.
     *
     * ### Behavior
     * - Duplicate middlewares are allowed and will be invoked multiple times if added more than once.
     * - A new [CoroutineScope] with a [SupervisorJob] and [Dispatchers.Main] is created and associated with
     *   the middleware, stored in [middlewareScopes].
     * - The addition is logged via [logAddMiddleware], [logCurrentMiddlewares], and [logCurrentMiddlewareScopes].
     *
     * ### Parameters
     * @param middleware The middleware to add, implementing the [Middleware] interface.
     *
     * ### Thread Safety
     * Must be called from the main thread, enforced by [checkMainThread].
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
     * Adds a middleware to the store with an associated tag, enabling it to intercept and process future dispatched actions.
     *
     * This method extends the functionality of [addMiddleware] by associating the middleware with a tag, which allows
     * for grouped management of middlewares, such as bulk removal via [removeMiddlewaresByTag]. The middleware is appended
     * to the store's list of middlewares and will be executed in the order it was added when actions are dispatched via
     * [dispatch] or [dispatchToMiddlewares]. Each invocation of this method with the same tag creates an independent
     * entry, meaning multiple middlewares can share the same tag and will all be affected by tag-based operations.
     *
     * ### Behavior
     * - **Ordering**: The middleware is added to the end of the [middlewares] list, ensuring it processes actions after
     *   previously added middlewares, regardless of tags.
     * - **Tagging**: The middleware is stored in a [taggedMiddlewares] map under the specified tag, enabling grouped
     *   operations. If the tag does not yet exist, a new entry is created; otherwise, the middleware is appended to the
     *   existing list for that tag.
     * - **Scope Management**: A new [CoroutineScope] with a [SupervisorJob] and [Dispatchers.Main] is created and
     *   associated with the middleware, stored in [middlewareScopes]. This scope enables asynchronous operations and
     *   is automatically cancelled when the middleware is removed (individually or by tag).
     * - **Removal Options**: The middleware can be removed individually using [removeMiddleware] or as part of a group
     *   using [removeMiddlewaresByTag]. Middlewares added via [addMiddleware] (without tags) are unaffected by tag-based
     *   removal.
     * - **Logging**: The addition is logged via [logAddMiddleware], with the current state of [middlewares] and
     *   [middlewareScopes] logged via [logCurrentMiddlewares] and [logCurrentMiddlewareScopes], respectively, for
     *   debugging and traceability.
     *
     * ### Parameters
     * @param middleware The middleware to add, implementing the [Middleware] interface. This object defines how actions
     *   are intercepted, potentially transformed, or used to trigger side effects or additional dispatches.
     * @param tag A string identifier used to group middlewares for bulk operations, such as removal via
     *   [removeMiddlewaresByTag]. Tags are case-sensitive and should be unique for distinct groups; reusing a tag adds
     *   the middleware to the existing group.
     *
     * ### Thread Safety
     * Must be called from the main thread, as determined by the [isMainThread] function provided during store creation.
     * This ensures thread-safe modification of the store's internal collections and scope management.
     *
     * ### Best Practices
     * - **Tag Usage**: Use meaningful tag names (e.g., `"logging"`, `"network"`) to clearly indicate the purpose or
     *   category of the middleware group, aiding maintainability.
     * - **Order Consideration**: Since middlewares execute in addition order, add tagged middlewares in the desired
     *   sequence relative to untagged ones.
     * - **Scope Usage**: Leverage the provided [CoroutineScope] for asynchronous tasks (e.g., API calls, delays) within
     *   the middleware, ensuring proper cleanup via scope cancellation on removal.
     *
     * ### Usage
     * ```kotlin
     * val loggingMiddleware: Middleware<CounterState> = { action, state, next, dispatch, scope ->
     *     println("Action: $action")
     *     next(action)
     * }
     * store.addMiddlewareWithTag(loggingMiddleware, "logging")
     * ```
     *
     * @throws CalledFromWrongThreadException If called from a thread other than the main thread, as determined by
     *   [isMainThread].
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
     * Removes a middleware from the store, preventing it from intercepting future actions.
     *
     * This method removes the first occurrence of the specified middleware and cancels its associated
     * [CoroutineScope], terminating any ongoing asynchronous operations launched within it.
     *
     * ### Behavior
     * - The removal is logged via [logRemoveMiddleware], [logCurrentMiddlewares], and [logCurrentMiddlewareScopes].
     * - The [CoroutineScope] is cancelled and removed from [middlewareScopes].
     *
     * ### Parameters
     * @param middleware The middleware to remove.
     *
     * ### Thread Safety
     * Must be called from the main thread, enforced by [checkMainThread].
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
     * Removes all middlewares associated with the specified tag from the store, preventing them from intercepting future actions.
     *
     * This method provides a mechanism to remove multiple middlewares in a single operation based on a shared tag,
     * complementing the individual removal capability of [removeMiddleware]. It targets middlewares previously added
     * via [addMiddlewareWithTag] with the specified tag, ensuring their removal from the execution chain and proper
     * cleanup of associated resources.
     *
     * ### Behavior
     * - **Targeted Removal**: Identifies all middlewares linked to the given tag in [taggedMiddlewares]. If the tag
     *   exists, each associated middleware is removed from the [middlewares] list.
     * - **Scope Cleanup**: For each removed middleware, its associated [CoroutineScope] in [middlewareScopes] is
     *   cancelled to terminate any ongoing coroutines (e.g., network requests, timers), and the scope is then removed
     *   from the map to prevent resource leaks.
     * - **Tag Deletion**: After removing all associated middlewares, the tag entry is cleared from [taggedMiddlewares].
     * - **No-Op Cases**: If the tag does not exist in [taggedMiddlewares] or has no associated middlewares (e.g., an
     *   empty list), the method returns immediately without effect, ensuring idempotency.
     * - **Logging**: Each middleware removal is logged via [logRemoveMiddleware], and the updated state of [middlewares]
     *   and [middlewareScopes] is logged via [logCurrentMiddlewares] and [logCurrentMiddlewareScopes], respectively,
     *   for debugging and verification.
     *
     * ### Parameters
     * @param tag The string tag identifying the group of middlewares to remove. This must match the tag used when adding
     *   middlewares via [addMiddlewareWithTag]. Tags are case-sensitive.
     *
     * ### Thread Safety
     * Must be called from the main thread, as determined by the [isMainThread] function provided during store creation.
     * This ensures thread-safe modification of the store's internal collections and scope management.
     *
     * ### Best Practices
     * - **Tag Consistency**: Ensure the tag matches exactly (including case) the one used in [addMiddlewareWithTag] to
     *   avoid missing intended middlewares.
     * - **Resource Management**: Use this method to clean up groups of middlewares (e.g., all logging or network-related
     *   ones) when they are no longer needed, relying on the automatic scope cancellation to free resources.
     * - **Idempotency**: Safe to call multiple times with the same tag; subsequent calls after the first removal have
     *   no effect.
     *
     * ### Usage
     * ```kotlin
     * store.addMiddlewareWithTag(loggingMiddleware, "logging")
     * store.addMiddlewareWithTag(anotherLoggingMiddleware, "logging")
     * store.removeMiddlewaresByTag("logging") // Removes both middlewares
     * ```
     *
     * @throws CalledFromWrongThreadException If called from a thread other than the main thread, as determined by
     *   [isMainThread].
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
     * Retrieves the current state of the store.
     *
     * This method provides a snapshot of the application state at the time of the call. It is thread-safe
     * and can be called from any thread, but the returned state should be treated as immutable to avoid
     * unintended side effects.
     *
     * ### Usage
     * ```kotlin
     * val currentState = store.getState()
     * // Use currentState read-only
     * ```
     *
     * ### Returns
     * @return The current state of the application, of type [S].
     */
    fun getState(): S = state
}
