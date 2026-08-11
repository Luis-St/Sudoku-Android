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
import net.luis.sudoku.data.local.MIGRATION_3_4
import net.luis.sudoku.data.local.MIGRATION_4_5
import net.luis.sudoku.data.local.MIGRATION_5_6
import net.luis.sudoku.data.local.dao.LearnProgressDao
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
			// see MIGRATION_2_3. Version 4 is migrated for the opposite reason: it deliberately clears
			// saved_games, and destructive fallback would take the history down with it. The fallback stays
			// for the versions before them, which nothing was released on and which are not worth writing
			// migrations backwards for. Version 5 clears the saved *chaos* games for generator 3 and keeps
			// the classic ones, which is a distinction destructive fallback could not make. Version 6 adds
			// the learn area's progress, which nothing else could rebuild once it exists.
			.addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
			.fallbackToDestructiveMigration(true)
			.build()

	@Provides
	fun provideSavedGameDao(database: AppDatabase): SavedGameDao = database.savedGameDao()

	@Provides
	fun provideStatisticsDao(database: AppDatabase): StatisticsDao = database.statisticsDao()

	@Provides
	fun providePendingDailyResultDao(database: AppDatabase): PendingDailyResultDao = database.pendingDailyResultDao()

	@Provides
	fun provideLearnProgressDao(database: AppDatabase): LearnProgressDao = database.learnProgressDao()
}
