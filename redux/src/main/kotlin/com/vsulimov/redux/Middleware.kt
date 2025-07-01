package com.vsulimov.redux

import kotlinx.coroutines.CoroutineScope

/**
 * A functional interface defining a middleware in a Redux state management system.
 *
 * A [Middleware] processes actions before they reach reducers, allowing for side effects, action
 * transformation, or additional action dispatching. It operates within a [CoroutineScope] to support
 * asynchronous operations, such as network calls or delayed tasks.
 *
 * @param S The type of the state managed by the Redux store.
 */
fun interface Middleware<S> {

    /**
     * Processes an action in the Redux pipeline.
     *
     * This function is called for each action dispatched to the store, allowing the middleware to
     * inspect, modify, or block the action, dispatch additional actions, or perform side effects
     * (e.g., logging, API calls). The middleware can use the provided [CoroutineScope] for
     * asynchronous operations. The [next] function should be called to pass the action (or a modified
     * version) to the next middleware or reducers, unless the action is intentionally blocked.
     *
     * @param action The [Action] being processed.
     * @param state The current state of the Redux store, providing context for the action.
     * @param next A function to pass the action (or a modified action) to the next middleware or reducers.
     * @param dispatch A function to dispatch new actions to the store, enabling side effects.
     * @param scope The [CoroutineScope] for handling asynchronous operations, ensuring proper lifecycle management.
     */
    fun invoke(action: Action, state: S, next: (Action) -> Unit, dispatch: (Action) -> Unit, scope: CoroutineScope)
}

/**
 * An abstract class for type-safe middleware that processes actions of a specific type.
 *
 * The [TypedMiddleware] ensures that only actions of type [A] are processed by the [invokeTyped]
 * method, while other actions are passed unchanged to the next middleware or reducers. This provides
 * type safety when handling specific action types in a Redux pipeline.
 *
 * @param A The specific [Action] type this middleware handles.
 * @param S The type of the state managed by the Redux store.
 * @param actionClass The [Class] of the action type [A] to handle.
 * @constructor Creates a [TypedMiddleware] that processes actions of the specified [actionClass].
 */
abstract class TypedMiddleware<A : Action, S>(private val actionClass: Class<A>) : Middleware<S> {

    /**
     * Processes an action, delegating to [invokeTyped] if the action matches the specified type.
     *
     * If the action is an instance of [actionClass], it is cast to type [A] and passed to [invokeTyped].
     * Otherwise, the action is forwarded unchanged to the [next] function.
     *
     * @param action The [Action] to process.
     * @param state The current state of the Redux store.
     * @param next A function to pass the action to the next middleware or reducers.
     * @param dispatch A function to dispatch new actions to the store.
     * @param scope The [CoroutineScope] for asynchronous operations.
     */
    final override fun invoke(
        action: Action,
        state: S,
        next: (Action) -> Unit,
        dispatch: (Action) -> Unit,
        scope: CoroutineScope
    ) {
        when {
            actionClass.isInstance(action) -> {
                val typedAction = actionClass.cast(action)
                typedAction?.let { invokeTyped(it, state, next, dispatch, scope) } ?: next(action)
            }
            else -> next(action)
        }
    }

    /**
     * Processes an action of the specific type [A].
     *
     * Subclasses must implement this method to define how actions of type [A] are handled. This method
     * is called only when the action matches the [actionClass] specified in the constructor.
     *
     * @param action The [Action] of type [A] to process.
     * @param state The current state of the Redux store.
     * @param next A function to pass the (possibly modified) action to the next middleware or reducers.
     * @param dispatch A function to dispatch new actions to the store.
     * @param scope The [CoroutineScope] for asynchronous operations.
     */
    abstract fun invokeTyped(
        action: A,
        state: S,
        next: (Action) -> Unit,
        dispatch: (Action) -> Unit,
        scope: CoroutineScope
    )
}
