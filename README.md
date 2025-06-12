# Android Redux

A lightweight, type-safe Redux implementation for Android applications, designed to manage application state in a
predictable and scalable way using Kotlin's powerful type system and concurrency features. The `Store` operates on a
state type that must implement the `ApplicationState` marker interface.

## Table of Contents

- [Features](#features)
- [Installation](#installation)
- [Usage](#usage)
- [API Reference](#api-reference)
- [Thread Safety](#thread-safety)
- [Testing](#testing)
- [License](#license)
- [Acknowledgments](#acknowledgments)

## Features

- **Immutable State Management**: Ensures state consistency by preventing direct mutations.
- **Type-Safe Reducers and Middlewares**: Leverages Kotlin's type system for safer, more reliable code.
- **Reactive State Updates**: Integrates with Kotlin Flows for real-time state observation.
- **Thread Safety**: Enforces main-thread usage for state updates and dispatches by default, but allows customization of
  the thread check.
- **Asynchronous Middleware Support**: Middlewares can launch asynchronous operations within a dedicated
  `CoroutineScope`, with automatic scope cancellation upon middleware removal.
- **Tagged Middleware Management**: Supports grouping middlewares by tags for bulk addition and removal, enhancing
  modularity and cleanup.
- **Verbose Logging**: Comprehensive logging for actions, middlewares, and reducers to aid debugging.
- **Extensible**: Supports custom reducers and middlewares for flexibility.

## Installation

To add Redux to your project, include the following repository in your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Other repositories here.
        maven {
            name = "vsulimovRepositoryReleases"
            url = uri("https://maven.vsulimov.com/releases")
        }
    }
}
```

Then, include the following dependency in your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.vsulimov:redux:1.0.0")
}
```

## Usage

Below is an example demonstrating how to set up and use Redux, including a middleware with asynchronous operations and
tagged middleware management:

```kotlin
// Define your application state
data class AppState(val count: Int = 0, val isLoading: Boolean = false)

// Define actions as a sealed class
sealed class AppAction : Action {
    data class Increment(val amount: Int) : AppAction()
    data class Decrement(val amount: Int) : AppAction()
    object StartLoading : AppAction()
    data class FinishLoading(val result: String) : AppAction()
}

// Create a reducer to handle actions
class AppReducer : Reducer<AppState> {
    override fun reduce(action: Action, state: AppState): AppState {
        return when (action) {
            is AppAction.Increment -> state.copy(count = state.count + action.amount)
            is AppAction.Decrement -> state.copy(count = state.count - action.amount)
            is AppAction.StartLoading -> state.copy(isLoading = true)
            is AppAction.FinishLoading -> state.copy(isLoading = false)
            else -> state
        }
    }
}

// Create a middleware for asynchronous operations
class AsyncMiddleware : Middleware<AppState> {
    override fun invoke(
        action: Action,
        state: AppState,
        next: (Action) -> Unit,
        dispatch: (Action) -> Unit,
        scope: CoroutineScope
    ) {
        when (action) {
            is AppAction.StartLoading -> {
                scope.launch {
                    // Simulate async work (e.g., network call)
                    kotlinx.coroutines.delay(1000)
                    dispatch(AppAction.FinishLoading(result = "Loaded"))
                }
            }
            else -> next(action)
        }
    }
}

// Create a logging middleware
class LoggingMiddleware : Middleware<AppState> {
    override fun invoke(
        action: Action,
        state: AppState,
        next: (Action) -> Unit,
        dispatch: (Action) -> Unit,
        scope: CoroutineScope
    ) {
        println("Action dispatched: $action, State: $state")
        next(action)
    }
}

// Initialize the store
val store = Store(
    initialState = AppState(),
    isMainThread = { Looper.getMainLooper().isCurrentThread() }
)
store.addReducer(AppReducer())
store.addMiddlewareWithTag(AsyncMiddleware(), "network")
store.addMiddlewareWithTag(LoggingMiddleware(), "logging")

// Dispatch actions to update the state
store.dispatch(AppAction.Increment(5))
store.dispatch(AppAction.StartLoading)

// Observe state changes using Kotlin Flows
lifecycleScope.launch {
    store.stateFlow.collect { state ->
        println("Current state: count=${state.count}, isLoading=${state.isLoading}")
    }
}

// Remove all middlewares with a specific tag when no longer needed (e.g., on cleanup)
store.removeMiddlewaresByTag("network")
store.removeMiddlewaresByTag("logging")
```

This example creates a counter application with an async loading operation and a logging middleware. The
`AsyncMiddleware` uses a `CoroutineScope` to perform asynchronous work, and both middlewares are grouped by tags ("
network" and "logging") for easy management. The `removeMiddlewaresByTag` method cleans up all middlewares associated
with a tag, cancelling their coroutine scopes.

## API Reference

- **`Store<S>`**: The central component that holds the state, manages reducers and middlewares, and provides a
  `stateFlow` for observing state changes.
    - Methods:
        - `addReducer(reducer: Reducer<S>)`: Adds a reducer to process actions.
        - `addMiddleware(middleware: Middleware<S>)`: Adds a middleware for action interception and async operations.
        - `addMiddlewareWithTag(middleware: Middleware<S>, tag: String)`: Adds a middleware with a tag for grouped
          management and bulk removal.
        - `hasMiddlewaresForTag(tag: String)`: Checks if there are middlewares associated with the specified tag.
        - `removeMiddleware(middleware: Middleware<S>)`: Removes a specific middleware and cancels its coroutine scope.
        - `removeMiddlewaresByTag(tag: String)`: Removes all middlewares associated with the specified tag and cancels
          their coroutine scopes.
        - `dispatch(action: Action)`: Dispatches an action to update the state.
        - `getState()`: Retrieves the current state.
- **`Reducer<S>`**: A pure function that takes the current state and an action, returning a new state.
- **`Middleware<S>`**: Intercepts actions to perform side effects or modify actions before they reach reducers. Supports
  a `CoroutineScope` for launching asynchronous operations, which is cancelled upon middleware removal.
- **`Logger`**: A utility for verbose logging of actions, middleware changes, and reducer operations, tagged with "
  Store" for easy debugging.

## Thread Safety

Redux enforces that all state modifications and action dispatches occur on the main thread, ensured by the`isMainThread`
check passed to the `Store`. This includes `addMiddlewareWithTag` and `removeMiddlewaresByTag`, which must also be
called on the main thread. State observation via `stateFlow` is thread-safe and can be performed from any thread.
Middlewares launching coroutines are provided with a `CoroutineScope` tied to `Dispatchers.Main` for thread-safe async
operations.

## Testing

The library is rigorously tested using `kotlin.test`, `mockito-kotlin`, and `kotlinx-coroutines-test`, achieving high
code coverage. Tests cover state management, action dispatching, middleware async operations, tagged middleware
management, and thread safety. The use of `StandardTestDispatcher` ensures reliable coroutine testing.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for more details.

## Acknowledgments

Redux is inspired by the original [Redux](https://redux.js.org/) for JavaScript, adapted to leverage Kotlin’s type
safety and concurrency primitives like Flows and Coroutines.
