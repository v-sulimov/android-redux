package com.vsulimov.android.lifecycle

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.vsulimov.redux.Action

/**
 * Activity lifecycle actions correspond to Android activity lifecycle events
 * and allows your application to react somehow when the activity lifecycle state is changed.
 *
 * **See Also:**
 * [Understand the Activity Lifecycle](https://developer.android.com/guide/components/activities/activity-lifecycle)
 */
sealed class ActivityLifecycleAction : Action {

    /**
     * Dispatched when [AppCompatActivity.onCreate] lifecycle
     * event occurs.
     */
    data class OnCreate(val savedInstanceState: Bundle?) : ActivityLifecycleAction()

    /**
     * Dispatched when [AppCompatActivity.onStart] lifecycle
     * event occurs.
     */
    object OnStart : ActivityLifecycleAction()

    /**
     * Dispatched when [AppCompatActivity.onResume] lifecycle
     * event occurs.
     */
    object OnResume : ActivityLifecycleAction()

    /**
     * Dispatched when [AppCompatActivity.onPause] lifecycle
     * event occurs.
     */
    data class OnPause(val isFinishing: Boolean) : ActivityLifecycleAction()

    /**
     * Dispatched when [AppCompatActivity.onStop] lifecycle
     * event occurs.
     */
    data class OnStop(val isFinishing: Boolean) : ActivityLifecycleAction()

    /**
     * Dispatched when [AppCompatActivity.onDestroy] lifecycle
     * event occurs.
     */
    data class OnDestroy(val isFinishing: Boolean) : ActivityLifecycleAction()
}
