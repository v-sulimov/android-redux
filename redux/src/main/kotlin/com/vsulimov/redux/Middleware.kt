package com.vsulimov.redux

import kotlinx.coroutines.CoroutineScope

/**
 * Defines a middleware, a component that intercepts actions before they reach reducers.
 *
 * Middlewares enable side effects (e.g., logging, API calls) and action transformations in the Redux flow.
 * They sit between the dispatch call and the reducers, allowing for:
 * - Asynchronous operations (e.g., network requests) within a provided CoroutineScope.
 * - Action filtering or modification.
 * - Logging or analytics.
 *
 * ### Type Parameters
 * @param S The type of the state managed by the Redux store. It must extend [ApplicationState].
 *
 * ### Best Practices
 * - Call `next(action)` to pass the action forward unless intentionally blocking it.
 * - Keep middleware focused on side effects, not state computation (leave that to reducers).
 * - Handle exceptions within the middleware to prevent application crashes.
 * - Use the provided `scope` for launching asynchronous operations; it will be cancelled when the middleware is removed.
 */
fun interface Middleware<S : ApplicationState> {
    /**
     * Processes an action, potentially performing side effects or transforming the action.
     *
     * This method is invoked for every dispatched action. Middlewares form a chain, and each can:
     * - Perform side effects (e.g., API calls, logging) using the provided `scope`.
     * - Pass the action to the next middleware or reducers via `next`.
     * - Block the action by not calling `next`.
     * - Dispatch additional actions via `dispatch`.
     * - Transform the action before passing it to `next`.
     *
     * **Note**: The order in which middlewares are added to the store determines their execution order.
     *
     * ### Parameters
     * @param action The action being dispatched.
     * @param state The current state.
     * @param next The function to call to pass the action to the next middleware or reducers.
     * @param dispatch The function to call to dispatch additional actions.
     * @param scope A CoroutineScope for launching async operations, cancelled when the middleware is removed.
     *
     * ### Example: Logging Middleware
     * ```kotlin
     * override fun invoke(action: Action, state: S, next: (Action) -> Unit, dispatch: (Action) -> Unit, scope: CoroutineScope) {
     *     println("Action dispatched: $action")
     *     next(action)
     * }
     * ```
     *
     * ### Example: Async Operation
     * ```kotlin
     * override fun invoke(action: Action, state: S, next: (Action) -> Unit, dispatch: (Action) -> Unit, scope: CoroutineScope) {
     *     if (action is SomeAsyncAction) {
     *         scope.launch {
     *             val result = someApiCall()
     *             dispatch(SomeResultAction(result))
     *         }
     *     }
     *     next(action)
     * }
     * ```
     */
    fun invoke(action: Action, state: S, next: (Action) -> Unit, dispatch: (Action) -> Unit, scope: CoroutineScope)
}

/**
 * An abstract class for creating middlewares that handle only a specific type of action, enhancing type safety.
 *
 * This class ensures the middleware processes only actions of type [A], passing others unchanged to the next step.
 *
 * ### Type Parameters
 * @param A The specific action type this middleware handles, must extend [Action].
 * @param S The type of the state managed by the Redux store. It must extend [ApplicationState].
 *
 * ### Best Practices
 * - Use for middlewares that target specific actions (e.g., async operations for a fetch action).
 * - Avoid blocking `next` unless explicitly intended.
 * - Note: The type check uses reflection (`isInstance`), which may have performance implications in high-frequency dispatch scenarios.
 *   For performance-critical code, consider using a functional approach with reified types.
 * - Use the provided `scope` for async operations; it will be cancelled when the middleware is removed.
 */
abstract class TypedMiddleware<A : Action, S : ApplicationState>(private val actionClass: Class<A>) : Middleware<S> {
    /**
     * Processes an action, delegating to [invokeTyped] if it matches type [A], otherwise passing it to `next`.
     *
     * This method is final to enforce type-safe behavior, ensuring consistent middleware chaining.
     *
     * ### Parameters
     * @param action The action to process.
     * @param state The current state.
     * @param next The function to pass the action to the next middleware or reducers.
     * @param dispatch The function to call to dispatch additional actions.
     * @param scope A CoroutineScope for launching async operations, cancelled when the middleware is removed.
     */
    @Suppress("UNCHECKED_CAST")
    final override fun invoke(
        action: Action,
        state: S,
        next: (Action) -> Unit,
        dispatch: (Action) -> Unit,
        scope: CoroutineScope
    ) {
        if (actionClass.isInstance(action)) {
            invokeTyped(action as A, state, next, dispatch, scope)
        } else {
            next(action)
        }
    }

    /**
     * Processes an action of type [A], allowing for side effects or action transformation.
     *
     * Subclasses must implement this method to define behavior for actions of type [A].
     *
     * ### Parameters
     * @param action The action being dispatched, guaranteed to be of type [A].
     * @param state The current state.
     * @param next The function to call to pass the action to the next middleware or reducers.
     * @param dispatch The function to call to dispatch additional actions.
     * @param scope A CoroutineScope for launching async operations, cancelled when the middleware is removed.
     *
     * ### Example
     * ```kotlin
     * override fun invokeTyped(action: A, state: S, next: (Action) -> Unit, dispatch: (Action) -> Unit, scope: CoroutineScope) {
     *     scope.launch {
     *         val result = someApiCall()
     *         dispatch(SomeResultAction(result))
     *     }
     *     next(action)
     * }
     * ```
     */
    abstract fun invokeTyped(
        action: A,
        state: S,
        next: (Action) -> Unit,
        dispatch: (Action) -> Unit,
        scope: CoroutineScope
    )
}
