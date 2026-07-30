package net.luis.sudoku.ui.common

/**
 * A deliberately loose check: enough to catch a typo before a round trip, never enough to reject an
 * address the server would have accepted. The server sends the verification mail, and a mail that never
 * arrives is the only real validation there is.
 */
fun isValidEmail(value: String): Boolean {
	val trimmed = value.trim()
	val at = trimmed.indexOf('@')
	if (at <= 0 || at != trimmed.lastIndexOf('@')) return false

	val domain = trimmed.substring(at + 1)
	return domain.length >= 3 && domain.contains('.') && !domain.startsWith('.') && !domain.endsWith('.') &&
		trimmed.none(Char::isWhitespace)
}
