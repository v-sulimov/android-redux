package com.vsulimov.redux

/**
 * Defines a reducer, a pure function responsible for computing a new state based on the action and current state.
 *
 * They must adhere to strict purity rules:
 * - **Purity**: Given the same input (action and state), they must always produce the same output without side effects.
 * - **Immutability**: They must not modify the input state but return a new state instance.
 * - **Determinism**: No external dependencies (e.g., network calls, random values) should influence the output.
 *
 * ### Type Parameters
 * @param S The type of the state managed by the Redux store. It must extend [ApplicationState]..
 *
 * ### Best Practices
 * - Avoid performing side effects (e.g., I/O operations) within reducers.
 * - Use immutable data structures (e.g., Kotlin data classes with `copy()`).
 * - Keep logic simple and focused on state transformation.
 * - Handle unknown actions gracefully by returning the current state unchanged.
 * - Be aware that reducers are invoked on the same thread as the `dispatch` call, typically the main thread in UI applications.
 */
fun interface Reducer<S : ApplicationState> {
    /**
     * Computes a new state by applying the given action to the current state.
     *
     * This method must be a pure function:
     * - It should not mutate the input `state`.
     * - It should not have side effects (e.g., logging, network calls).
     * - It should depend only on `action` and `state`, not on external variables or randomness.
     *
     * **Note**: When multiple reducers are added to the store, they are applied in the order they were added.
     *
     * ### Parameters
     * @param action The action to process, which carries the information needed to update the state.
     * @param state The current state of the application. Treat this as immutable and do not modify it.
     *
     * ### Returns
     * A new state object reflecting the changes dictated by the action. Always return a new instance,
     * even if the state does not change, to maintain consistency.
     *
     * ### Example: Handling Multiple Actions
     * ```kotlin
     * override fun reduce(action: Action, state: CounterState): CounterState {
     *     return when (action) {
     *         is Increment -> state.copy(count = state.count + action.amount)
     *         is Decrement -> state.copy(count = state.count - action.amount)
     *         else -> state
     *     }
     * }
     * ```
     */
    fun reduce(action: Action, state: S): S
}

/**
 * An abstract class for creating reducers that handle only a specific type of action, enhancing type safety.
 *
 * This class simplifies reducer implementation by filtering actions to a specific type, reducing boilerplate
 * and preventing errors from unhandled action types.
 *
 * ### Type Parameters
 * @param A The specific action type this reducer handles, must extend [Action] (e.g., `Increment`).
 * @param S The type of the state managed by the Redux store. It must extend [ApplicationState]..
 *
 * ### Best Practices
 * - Use this class when a reducer should respond to only one or a few action types.
 * - Ensure `reduceTyped` remains pure and immutable.
 * - Note: The type check uses reflection (`isInstance`), which may have performance implications in high-frequency dispatch scenarios.
 *   For performance-critical code, consider using a functional approach with reified types.
 */
abstract class TypedReducer<A : Action, S : ApplicationState>(private val actionClass: Class<A>) : Reducer<S> {
    /**
     * Processes the current action and state, delegating to [reduceTyped] if the action matches type [A].
     *
     * This method is final to enforce type-safe behavior: it checks the action type and either processes it
     * or returns the unchanged state. Subclasses cannot override this logic, ensuring consistency.
     *
     * ### Parameters
     * @param action The action to process, which may or may not be of type [A].
     * @param state The current state of the application.
     *
     * ### Returns
     * A new state if the action is of type [A] and handled by [reduceTyped]; otherwise, the original state.
     */
    @Suppress("UNCHECKED_CAST")
    final override fun reduce(action: Action, state: S): S = if (actionClass.isInstance(action)) {
        reduceTyped(action as A, state)
    } else {
        state
    }

    /**
     * Computes a new state based on the current state and a specific action of type [A].
     *
     * Subclasses must implement this method to define how the state changes in response to actions of type [A].
     * Like [reduce], this method must be pure and return a new state instance.
     *
     * ### Parameters
     * @param action The action of type [A] to process.
     * @param state The current state of the application.
     *
     * ### Returns
     * A new state object reflecting the changes from the action.
     *
     * ### Example
     * ```kotlin
     * override fun reduceTyped(action: Increment, state: CounterState): CounterState {
     *     return state.copy(count = state.count + action.amount)
     * }
     * ```
     */
    abstract fun reduceTyped(action: A, state: S): S
}
