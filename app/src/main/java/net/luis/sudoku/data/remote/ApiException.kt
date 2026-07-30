package net.luis.sudoku.data.remote

/** The server's `{ error, message, details }` contract (server-spec §6) - `code` is the stable name to switch on. */
class ApiException(val code: String, override val message: String?) : Exception(message) {

	companion object {

		/**
		 * Client-side pseudo-code for a call that never reached the server at all - connection refused,
		 * timeout, unknown host. There is no `ErrorResponse` for those, but every error surface switches on
		 * a code, and "the server is unreachable" has to be reportable like any other failure rather than
		 * escaping the coroutine and killing the app.
		 */
		const val NETWORK_ERROR = "NETWORK_ERROR"
	}
}
