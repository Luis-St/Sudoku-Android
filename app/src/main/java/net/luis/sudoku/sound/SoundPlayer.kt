package net.luis.sudoku.sound

import javax.inject.Inject

/** feature-spec §6b: number entered (pen/pencil), win, lose. Input clips stay under ~200ms once supplied. */
enum class SoundEvent { PEN_ENTRY, PENCIL_ENTRY, WIN, LOSE }

/**
 * Infrastructure-only per §6b: wired into every call site now, swapped for a real implementation later
 * with zero call-site changes - only [net.luis.sudoku.di.SoundModule]'s binding changes.
 */
interface SoundPlayer {
	fun play(event: SoundEvent)
}

/** The only binding until clips are supplied - `SoundPoolSoundPlayer` will preload them at startup. */
class NoOpSoundPlayer @Inject constructor() : SoundPlayer {
	override fun play(event: SoundEvent) = Unit
}
