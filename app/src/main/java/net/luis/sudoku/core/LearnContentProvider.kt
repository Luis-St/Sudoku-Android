package net.luis.sudoku.core

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.luis.sudoku.learn.LearnAsset
import net.luis.sudoku.learn.LearnPuzzle
import net.luis.sudoku.learn.LearnPuzzleGenerator
import net.luis.sudoku.solver.Technique
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where a learn puzzle came from, which is the difference between a wait and no wait at all.
 *
 * [BUNDLED] is the fourteen puzzles per technique that ship with the app: read from assets, parsed in
 * milliseconds, and identical on every device, which is what lets the training have fixed exercises and
 * fixed progress. [GENERATED] is the player asking for a fresh one, which runs the same search the export
 * ran and can take seconds or fail - see [generate].
 */
enum class LearnPuzzleOrigin { BUNDLED, GENERATED }

/**
 * The single place the learn area's content comes from, and the only place the bundled assets are read or a
 * learn puzzle is generated.
 *
 * It exists for the same reason [PuzzleProvider] does: both jobs are expensive enough to freeze a frame and
 * were going to be called from several view models, so the hop to [Dispatchers.IO] and [Dispatchers.Default]
 * happens once, here, rather than at each call site.
 *
 * Assets are cached after the first read. There are 41 of them at about 10 KB each, a technique's file is
 * read the moment its wiki page opens, and a player moving between a technique and its training re-reads the
 * same file several times over. Holding the parsed content costs a few hundred kilobytes and saves every one
 * of those reads.
 */
@Singleton
class LearnContentProvider @Inject constructor(
	@ApplicationContext private val context: Context
) {

	private val cache = mutableMapOf<Technique, LearnAsset>()
	private val lock = Any()

	/**
	 * The bundled content of one technique: its five worked examples and its nine exercises.
	 *
	 * @throws IOException if the asset is missing or unreadable, which means the app was built wrong rather
	 *   than anything the player did
	 */
	suspend fun assetOf(technique: Technique): LearnAsset {
		synchronized(lock) { cache[technique] }?.let { return it }

		val asset = withContext(Dispatchers.IO) {
			val json = context.assets.open("$ASSET_DIRECTORY/${LearnAsset.fileNameOf(technique)}")
				.bufferedReader()
				.use { it.readText() }
			LearnAsset.read(json)
		}
		// Two callers racing on the same technique parse it twice and one result is dropped. That is cheaper
		// than holding a lock across the read, and the two results are equal anyway: the asset is a file.
		synchronized(lock) { cache.getOrPut(technique) { asset } }
		return asset
	}

	/**
	 * One exercise of a technique's training.
	 *
	 * @param level the one-based training level
	 * @param subLevel the zero-based exercise within it
	 */
	suspend fun exercise(technique: Technique, level: Int, subLevel: Int): LearnPuzzle =
		assetOf(technique).exercise(level, subLevel)

	/**
	 * A puzzle generated on this device, for the player who wants another go at a technique.
	 *
	 * This is the same search the bundled content was exported with, and it is a search: a common technique
	 * turns up in well under a second, a rare one may take far longer, and some never turn up inside a budget
	 * a phone can offer. So it returns null rather than pretending, and the caller says so plainly.
	 *
	 * The seed is the caller's, so a retry after a failure looks somewhere new rather than repeating the
	 * search that just failed.
	 */
	suspend fun generate(technique: Technique, seed: Long): LearnPuzzle? = withContext(Dispatchers.Default) {
		LearnPuzzleGenerator.generate(technique, seed, LearnPuzzleGenerator.Budget.standard()).orElse(null)
	}

	private companion object {

		const val ASSET_DIRECTORY = "learn"
	}
}
