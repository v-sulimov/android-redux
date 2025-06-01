package com.vsulimov.redux

/**
 * Represents an action in the Redux pattern, serving as a payload of information that triggers state changes.
 *
 * Actions are the only source of information for the store to update the state. They should be immutable
 * to ensure predictability and thread safety. Additionally, actions should be serializable to support
 * features like logging, debugging, or state persistence across app restarts.
 *
 * ### Best Practices
 * - **Immutability**: Use data classes or sealed classes with immutable properties to define actions.
 * - **Simplicity**: Keep actions as simple data carriers without business logic.
 * - **Uniqueness**: Ensure each action type is distinct to avoid unintended state changes.
 *
 * ### Example
 * ```kotlin
 * sealed class CounterAction : Action
 * data class Increment(val amount: Int) : CounterAction()
 * data class Decrement(val amount: Int) : CounterAction()
 * ```
 */
interface Action
