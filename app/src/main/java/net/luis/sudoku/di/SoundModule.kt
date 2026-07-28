package net.luis.sudoku.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.luis.sudoku.sound.NoOpSoundPlayer
import net.luis.sudoku.sound.SoundPlayer
import javax.inject.Singleton

/** The one place a future `SoundPoolSoundPlayer` binding swap happens (feature-spec §6b) - no call-site changes. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SoundModule {

	@Binds
	@Singleton
	abstract fun bindSoundPlayer(impl: NoOpSoundPlayer): SoundPlayer
}
