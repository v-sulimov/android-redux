package com.vsulimov.redux.data

import com.vsulimov.redux.Action

/**
 * Test action used in unit tests.
 * @param name the name of the action, defaults to "Test Action".
 * @param increment the value by which to increment, defaults to 1.
 */
data class TestAction(val name: String = "Test Action", val increment: Int = 1) : Action
