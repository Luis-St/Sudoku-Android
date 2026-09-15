package net.luis.sudoku.domain

import net.luis.sudoku.core.GameSession
import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.grid.GridSize
import net.luis.sudoku.grid.Variant
import net.luis.sudoku.key.PuzzleKey

/**
 * Fixed 9x9 boards for the hint tests, so the hint is checked on the same positions every run instead of on
 * whatever the generator produces.
 *
 * Generated once and kept here. A hint is a statement about one position, and a fixture that moved with the
 * generator would stop testing the position a failure was found on. The same puzzles are walked in the Lib's
 * `HintEngineTest`, where they were picked to reach 34 techniques between them, the chains and ALS techniques
 * included.
 */
object HintFixtures {

	/**
	 * The board a hint once explained wrongly: it drew a pointing pair on 9 in box 2 while marking r1c8, which a
	 * different pointing pair, on 2 in box 6, solves.
	 */
	const val REPORTED = "059700000030065018102040570008000600043002000500400300975030062000009030300000890"

	val WALKED: List<String> = listOf(
		"000060080100000200040002307504600001009070002020001008097000000050090010000053004",
		"010000000074200500026083000001820003000004020203106040080040000050038069000000007",
		"000015000009000008020070006000900802687200400002000030904700000005000010700020000",
		"080400209905000000070000800000900001800600000000540600760000020100806074000000510",
		"200400000083071400001806007600008700000000001009040500000000630500007100030014000",
		"004800000000960000071500309000001405049006080000700000000000002307000048098000000"
	)

	fun session(givens: String): GameSession = GameSession.fromGivens(
		PuzzleKey.of(GridSize.NINE, Variant.CLASSIC, Difficulty.ONE, 0L),
		IntArray(givens.length) { givens[it] - '0' }
	)

	/** The row-major index of r[row]c[column], both counted from 1 as a player reads them. */
	fun cell(row: Int, column: Int): Int = (row - 1) * 9 + column - 1
}
