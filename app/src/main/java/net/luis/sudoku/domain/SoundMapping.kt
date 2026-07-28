package net.luis.sudoku.domain

import net.luis.sudoku.sound.SoundEvent

/**
 * Which [SoundEvent] a board edit fires, if any (feature-spec §6b's two input clips) - pulled out as a
 * pure function so it's testable without a [SoundPlayer][net.luis.sudoku.sound.SoundPlayer] or a ViewModel.
 */
fun soundEventFor(action: TapAction): SoundEvent? = when (action) {
	is TapAction.EnterPen -> SoundEvent.PEN_ENTRY
	is TapAction.TogglePencil -> SoundEvent.PENCIL_ENTRY
	TapAction.None -> null
}
