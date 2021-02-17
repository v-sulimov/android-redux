package com.vsulimov.android.activity

import android.os.Bundle
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity
import com.vsulimov.android.lifecycle.ActivityLifecycleAction
import com.vsulimov.redux.Store

/**
 * Base implementation of Redux compatible [AppCompatActivity]
 * which dispatch [ActivityLifecycleAction] every time it's lifecycle state changed.
 * You can extend this class if your application needs to be lifecycle aware in a Redux way.
 */
abstract class AppCompatActivity<State> : AppCompatActivity() {

    /**
     * Returns current application store.
     */
    abstract fun getStore(): Store<State>

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getStore().dispatch(ActivityLifecycleAction.OnCreate(savedInstanceState))
    }

    @CallSuper
    override fun onStart() {
        super.onStart()
        getStore().dispatch(ActivityLifecycleAction.OnStart)
    }

    @CallSuper
    override fun onResume() {
        super.onResume()
        getStore().dispatch(ActivityLifecycleAction.OnResume)
    }

    @CallSuper
    override fun onPause() {
        getStore().dispatch(ActivityLifecycleAction.OnPause(isFinishing))
        super.onPause()
    }

    @CallSuper
    override fun onStop() {
        getStore().dispatch(ActivityLifecycleAction.OnStop(isFinishing))
        super.onStop()
    }

    @CallSuper
    override fun onDestroy() {
        getStore().dispatch(ActivityLifecycleAction.OnDestroy(isFinishing))
        super.onDestroy()
    }
}
