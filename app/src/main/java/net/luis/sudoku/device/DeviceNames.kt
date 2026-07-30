package net.luis.sudoku.device

import android.os.Build

/**
 * The device label sent when registering, linking or recovering (feature-spec §9.3's device list).
 *
 * The label is what the player picks a device out of the list by, so leaving it blank leaves them with
 * a list of "Unnamed device" rows they cannot revoke confidently. Generating one from the hardware is
 * both better than blank and still editable before it is sent.
 */
object DeviceNames {

	fun default(): String = format(Build.MANUFACTURER, Build.MODEL)

	/**
	 * Pure so it can be tested off-device: `Build.MODEL` is empty under a JVM test, and on real hardware
	 * it often already starts with the manufacturer ("Pixel 8" on a Google device is the model alone,
	 * while a Samsung reports manufacturer "samsung" and model "SM-S911B").
	 */
	fun format(manufacturer: String?, model: String?): String {
		val brand = manufacturer?.trim().orEmpty()
		val device = model?.trim().orEmpty()

		return when {
			device.isEmpty() && brand.isEmpty() -> FALLBACK
			device.isEmpty() -> brand.replaceFirstChar(Char::uppercase)
			brand.isEmpty() || device.startsWith(brand, ignoreCase = true) -> device
			else -> "${brand.replaceFirstChar(Char::uppercase)} $device"
		}
	}

	private const val FALLBACK = "Android device"
}
