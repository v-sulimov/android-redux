package com.vsulimov.redux

/**
 * A marker interface for actions in a Redux state management system.
 *
 * An [Action] represents an intent to change the application's state. It is typically implemented
 * by data classes or objects that encapsulate the data needed for state updates. Actions are dispatched
 * to a Redux store, where they are processed by middlewares and reducers to update the application state.
 *
 * This interface is used as a contract for all action types in the system, ensuring type safety and
 * consistency in the Redux pipeline. Implementations should provide specific data relevant to the action.
 */
interface Action
