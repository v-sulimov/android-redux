package com.vsulimov.redux.data

import com.vsulimov.redux.Action

/**
 * Represents another action that can be dispatched in the Redux store.
 *
 * This data class implements the [Action] interface and carries a [String] value.
 * It serves as an example of a concrete action type.
 *
 * @property value The string payload associated with this action. Defaults to an empty string.
 */
data class OtherAction(val value: String = "") : Action
