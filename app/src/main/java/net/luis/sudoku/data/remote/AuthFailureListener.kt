package net.luis.sudoku.data.remote

/**
 * What [ApiClient] and [net.luis.sudoku.data.remote.match.MatchSocketClient] tell when a request or a
 * socket comes back with an authentication failure. Implemented by [SessionGuard].
 *
 * An interface rather than the guard itself so that the transport classes stay plain JVM objects: the
 * guard reaches DataStore and the Android keystore, and both are testable against a real server only with
 * a device. [NONE] is what the tests pass.
 */
fun interface AuthFailureListener {

	/**
	 * @param code the server's error code, or a WebSocket close reason - the same names either way
	 */
	suspend fun onApiError(code: String)

	companion object {

		val NONE = AuthFailureListener { }
	}
}
