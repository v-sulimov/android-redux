package com.vsulimov.redux.data

import com.vsulimov.redux.Action

data class TestAction(
    val name: String = "Test Action",
    val increment: Int = 1
) : Action
