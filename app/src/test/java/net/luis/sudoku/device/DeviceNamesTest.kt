package net.luis.sudoku.device

import org.junit.Assert.assertEquals
import org.junit.Test

/** Server item 2: a device that was never named still has to be recognisable in the device list. */
class DeviceNamesTest {

	@Test
	fun format_combinesManufacturerAndModel() {
		assertEquals("Samsung SM-S911B", DeviceNames.format("samsung", "SM-S911B"))
	}

	@Test
	fun format_doesNotRepeatAManufacturerTheModelAlreadyNames() {
		assertEquals("OnePlus 12", DeviceNames.format("OnePlus", "OnePlus 12"))
		// Google's models do not carry the brand, so it is prepended rather than dropped.
		assertEquals("Google Pixel 8", DeviceNames.format("Google", "Pixel 8"))
	}

	@Test
	fun format_fallsBackWhenTheBuildFieldsAreEmpty() {
		// Exactly what a JVM unit test sees, and what an emulator image can report.
		assertEquals("Android device", DeviceNames.format("", ""))
		assertEquals("Android device", DeviceNames.format(null, null))
	}

	@Test
	fun format_usesWhicheverHalfIsPresent() {
		assertEquals("SM-S911B", DeviceNames.format("", "SM-S911B"))
		assertEquals("Samsung", DeviceNames.format("samsung", ""))
	}

	@Test
	fun format_trimsSurroundingWhitespace() {
		assertEquals("Samsung SM-S911B", DeviceNames.format("  samsung ", " SM-S911B "))
	}
}
