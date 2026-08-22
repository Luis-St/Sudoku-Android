package net.luis.sudoku.notification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Whether the app may post notifications, asked in a way that is also true on the versions of Android that
 * have no such permission.
 *
 * `POST_NOTIFICATIONS` was introduced in API 33. Below that, notifications need no runtime permission at
 * all: they are on unless the player turns the app's channel off in system settings, which is not something
 * an app can ask about. The platform does not merely return "granted" for an unknown permission name
 * though, it returns *denied* - and `RequestPermission` for one hands back `false` without ever showing a
 * dialog. So an unguarded check reads as "the player said no" on every device below Android 13, which
 * would leave the daily reminder switch impossible to turn on and the reminder itself never posted.
 */
internal object NotificationPermission {

	/**
	 * True when the runtime permission exists on this device and therefore has to be asked for.
	 */
	val isRequired: Boolean
		get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

	/**
	 * True when notifications may be posted: below API 33 always, above it only once the player has granted
	 * `POST_NOTIFICATIONS`.
	 */
	fun isGranted(context: Context): Boolean {
		if (!this.isRequired) {
			return true
		}
		return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
			PackageManager.PERMISSION_GRANTED
	}
}
