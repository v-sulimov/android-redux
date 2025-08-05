# Changelog

All notable changes to this project will be documented in this file.

## [1.0.2] - 05/08/2025

### Changed
- Updated mockito-kotlin to version 6.0.0
- Updated Android Gradle Plugin to version 8.12.0
- Updated Gradle to version 9.0.0

## [1.0.1] - 15/07/2025

### Changed
- Updated Gradle wrapper to version 8.14.3
- Updated Android Gradle Plugin to version 8.11.1

## [1.0.0] - 01/07/2025

### Added
- Initial release of the Redux library, providing a Redux-inspired state management solution for Android applications.
- `Store` class to manage the application state, actions, middlewares, and reducers.
- Support for defining actions that implement the `Action` interface.
- `Reducer` interface for pure functions that update the state based on actions.
- `Middleware` interface for intercepting actions, performing side effects, and dispatching additional actions.
- Type-safe variants `TypedReducer` and `TypedMiddleware` for handling specific action types.
- Thread safety mechanisms to ensure that state updates and dispatches occur on the main thread.
- Asynchronous operation support in middlewares using Kotlin coroutines.
- Logging utilities to monitor state changes, action dispatches, and middleware operations.
- `CalledFromWrongThreadException` to handle and notify about thread violations.
- Dynamic addition and removal of reducers and middlewares at runtime.
- Tagged middlewares for organized management and removal.
- Direct dispatch methods to middlewares or reducers for advanced use cases.
- State observation through a `StateFlow` for reactive UI updates.
