package net.luis.sudoku.data.remote

/** The server's `{ error, message, details }` contract (server-spec §6) - `code` is the stable name to switch on. */
class ApiException(val code: String, override val message: String?) : Exception(message)
