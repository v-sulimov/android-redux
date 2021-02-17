package com.vsulimov.android

import com.vsulimov.android.activity.AppCompatActivity
import com.vsulimov.redux.Store

/**
 * Redux application test activity.
 */
class TestActivity : AppCompatActivity<ApplicationState>() {

    override fun getStore(): Store<ApplicationState> =
        (application as TestApplication).store
}
