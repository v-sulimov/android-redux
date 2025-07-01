package com.vsulimov.redux

/**
 * A functional interface defining a reducer in a Redux state management system.
 *
 * A [Reducer] processes an [Action] and the current state to produce a new state. Reducers are pure
 * functions, meaning they must be deterministic and free of side effects, ensuring predictable state
 * transitions in the Redux store.
 *
 * @param S The type of the state managed by the Redux store.
 * @return The new state after applying the action.
 */
fun interface Reducer<S> {

    /**
     * Reduces an action and the current state to produce a new state.
     *
     * This function defines how the state should change in response to the given [action]. It must
     * be a pure function, avoiding side effects such as network calls or mutations of external state.
     * The reducer should return a new state instance, leaving the input [state] unchanged.
     *
     * @param action The [Action] to process.
     * @param state The current state of the Redux store.
     * @return The new state after applying the action.
     */
    fun reduce(action: Action, state: S): S
}

/**
 * An abstract class for type-safe reducers that process actions of a specific type.
 *
 * The [TypedReducer] ensures that only actions of type [A] are processed by the [reduceTyped]
 * method, while other actions result in the original state being returned unchanged. This provides
 * type safety when handling specific action types in a Redux pipeline.
 *
 * @param A The specific [Action] type this reducer handles.
 * @param S The type of the state managed by the Redux store.
 * @param actionClass The [Class] of the action type [A] to handle.
 * @constructor Creates a [TypedReducer] that processes actions of the specified [actionClass].
 */
abstract class TypedReducer<A : Action, S>(private val actionClass: Class<A>) : Reducer<S> {

    /**
     * Processes an action, delegating to [reduceTyped] if the action matches the specified type.
     *
     * If the action is an instance of [actionClass], it is cast to type [A] and passed to [reduceTyped].
     * Otherwise, the original [state] is returned unchanged, ensuring that non-matching actions do not
     * affect the state.
     *
     * @param action The [Action] to process.
     * @param state The current state of the Redux store.
     * @return The new state after applying the action, or the original state if the action does not match.
     */
    final override fun reduce(action: Action, state: S): S = when {
        actionClass.isInstance(action) -> {
            val typedAction = actionClass.cast(action)
            typedAction?.let { reduceTyped(it, state) } ?: state
        }
        else -> state
    }

    /**
     * Processes an action of the specific type [A] to produce a new state.
     *
     * Subclasses must implement this method to define how actions of type [A] modify the state.
     * This method is called only when the action matches the [actionClass] specified in the constructor.
     * It must be a pure function, returning a new state instance without modifying the input [state].
     *
     * @param action The [Action] of type [A] to process.
     * @param state The current state of the Redux store.
     * @return The new state after applying the action.
     */
    abstract fun reduceTyped(action: A, state: S): S
}
