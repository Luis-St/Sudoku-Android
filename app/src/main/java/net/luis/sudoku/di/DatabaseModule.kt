package net.luis.sudoku.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.luis.sudoku.data.local.AppDatabase
import net.luis.sudoku.data.local.MIGRATION_2_3
import net.luis.sudoku.data.local.dao.PendingDailyResultDao
import net.luis.sudoku.data.local.dao.SavedGameDao
import net.luis.sudoku.data.local.dao.StatisticsDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

	@Provides
	@Singleton
	fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
		Room.databaseBuilder(context, AppDatabase::class.java, "sudoku.db")
			// game_results is the player's whole history, so version 3 is migrated rather than dropped -
			// see MIGRATION_2_3. The fallback stays for the versions before it, which nothing was released
			// on and which are not worth writing migrations backwards for.
			.addMigrations(MIGRATION_2_3)
			.fallbackToDestructiveMigration(true)
			.build()

	@Provides
	fun provideSavedGameDao(database: AppDatabase): SavedGameDao = database.savedGameDao()

	@Provides
	fun provideStatisticsDao(database: AppDatabase): StatisticsDao = database.statisticsDao()

	@Provides
	fun providePendingDailyResultDao(database: AppDatabase): PendingDailyResultDao = database.pendingDailyResultDao()
}
