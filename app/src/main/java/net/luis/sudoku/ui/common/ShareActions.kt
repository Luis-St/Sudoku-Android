package net.luis.sudoku.ui.common

import android.content.Context
import android.content.Intent

/**
 * Handing a code to somebody else (general item 2): straight to the system share sheet, with no popup of
 * our own in front of it. Opens the share sheet on [text].
 *
 * There used to be a `CodeShareDialog` here, offering Copy and Share side by side. Android's share sheet
 * already contains Copy, so that dialog was a screen the player had to get through to reach the screen that
 * does the job - and it was in the way of every share in the app. What is left is this call, made directly
 * by whoever produced the code.
 *
 * A `copyToClipboard` helper used to sit beside it for the one screen that drew a code inline with its own
 * copy button. The match lobby was that screen and no longer has the button, so that reasoning now covers
 * every share in the app without an exception to it.
 *
 * [text] is a sentence, not a bare code: the sheet drops it into a message, an email or a note, where a
 * lone string of characters says nothing about what it is or what to do with it.
 */
internal fun shareText(context: Context, text: String) {
	val intent = Intent(Intent.ACTION_SEND).apply {
		type = "text/plain"
		putExtra(Intent.EXTRA_TEXT, text)
	}
	context.startActivity(Intent.createChooser(intent, null))
}
