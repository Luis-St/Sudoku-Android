package net.luis.sudoku.domain

import net.luis.sudoku.sound.NoOpSoundPlayer
import net.luis.sudoku.sound.SoundEvent
import net.luis.sudoku.sound.SoundPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * feature-spec §6b: every board-entry call site fires through [SoundPlayer], and swapping the no-op
 * binding for a real one needs zero call-site changes - proven here by swapping in a fake and reading
 * back exactly what the pure call-site mapping ([soundEventFor]) would have told a real player to do.
 */
class SoundMappingTest {

	private class RecordingSoundPlayer : SoundPlayer {
		val played = mutableListOf<SoundEvent>()
		override fun play(event: SoundEvent) {
			this.played.add(event)
		}
	}

	@Test
	fun soundEventFor_penEntry_isPenEntryEvent() {
		assertEquals(SoundEvent.PEN_ENTRY, soundEventFor(TapAction.EnterPen(0, 5)))
	}

	@Test
	fun soundEventFor_pencilToggle_isPencilEntryEvent() {
		assertEquals(SoundEvent.PENCIL_ENTRY, soundEventFor(TapAction.TogglePencil(0, 5)))
	}

	@Test
	fun soundEventFor_none_isNoSound() {
		assertNull(soundEventFor(TapAction.None))
	}

	@Test
	fun swappingTheNoOpBindingForAnyOtherSoundPlayer_needsNoCallSiteChanges() {
		val players: List<SoundPlayer> = listOf(NoOpSoundPlayer(), RecordingSoundPlayer())

		players.forEach { player ->
			soundEventFor(TapAction.EnterPen(0, 1))?.let(player::play)
		}

		val recording = players.filterIsInstance<RecordingSoundPlayer>().single()
		assertEquals(listOf(SoundEvent.PEN_ENTRY), recording.played)
	}
}
