package net.luis.sudoku.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.luis.sudoku.data.local.DailyDataStore
import net.luis.sudoku.data.local.ServerConfigDataStore
import net.luis.sudoku.data.local.SettingsDataStore
import javax.inject.Singleton

private val Context.currencyDataStore: DataStore<Preferences> by preferencesDataStore(name = "currency")
private val Context.dailyDataStore: DataStore<Preferences> by preferencesDataStore(name = "daily")
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
private val Context.serverConfigDataStore: DataStore<Preferences> by preferencesDataStore(name = "server_config")

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

	@Provides
	@Singleton
	fun provideCurrencyDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
		context.currencyDataStore

	@Provides
	@Singleton
	@DailyDataStore
	fun provideDailyDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
		context.dailyDataStore

	@Provides
	@Singleton
	@SettingsDataStore
	fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
		context.settingsDataStore

	@Provides
	@Singleton
	@ServerConfigDataStore
	fun provideServerConfigDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
		context.serverConfigDataStore
}
