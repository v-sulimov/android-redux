# Android Redux

A lightweight, type-safe Redux implementation for Android applications, designed to manage application state in a
predictable and scalable way using Kotlin's powerful type system and concurrency features.

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
- **Thread Safety**: Enforces main-thread usage for state updates and dispatches.
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

Below is a simple example demonstrating how to set up and use Redux:

```kotlin
// Define your application state
data class AppState(val count: Int = 0)

// Define actions as a sealed class
sealed class AppAction : Action {
    data class Increment(val amount: Int) : AppAction()
    data class Decrement(val amount: Int) : AppAction()
}

// Create a reducer to handle actions
class AppReducer : Reducer<AppState> {
    override fun reduce(action: Action, state: AppState): AppState {
        return when (action) {
            is AppAction.Increment -> state.copy(count = state.count + action.amount)
            is AppAction.Decrement -> state.copy(count = state.count - action.amount)
            else -> state
        }
    }
}

// Initialize the store
val store = Store(initialState = AppState(), isMainThread = { Looper.getMainLooper().isCurrentThread() })
store.addReducer(AppReducer())

// Dispatch actions to update the state
store.dispatch(AppAction.Increment(5))
store.dispatch(AppAction.Decrement(2))

// Observe state changes using Kotlin Flows
lifecycleScope.launch {
    store.stateFlow.collect { state ->
        println("Current count: ${state.count}")
    }
}
```

This example creates a basic counter application where the state (`count`) is updated via `Increment` and `Decrement`
actions and observed in real-time.

## API Reference

- **`Store<S>`**: The central component that holds the state, manages reducers and middlewares, and provides a
  `stateFlow` for observing state changes.
- **`Reducer<S>`**: A pure function that takes the current state and an action, returning a new state.
- **`Middleware<S>`**: Intercepts actions to perform side effects or modify actions before they reach the reducers.

## Thread Safety

Redux enforces that all state modifications and action dispatches occur on the main thread. This is ensured by
the `isMainThread` check passed to the `Store` during initialization. State observation via `stateFlow` is thread-safe
and can be performed from any thread.

## Testing

The library is rigorously tested using `kotlin.test` and `mockito-kotlin`, achieving high code coverage and ensuring
reliability across various use cases.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for more details.

## Acknowledgments

Redux is inspired by the original [Redux](https://redux.js.org/) for JavaScript, adapted to take advantage of
Kotlin’s type safety and concurrency primitives like Flows.
