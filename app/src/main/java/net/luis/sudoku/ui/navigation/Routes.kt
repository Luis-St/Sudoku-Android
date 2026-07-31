package net.luis.sudoku.ui.navigation

import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.grid.GridSize
import net.luis.sudoku.grid.Variant

/**
 * Every destination in the app. Replaces the hand-rolled `enum class Screen` + `when` block that
 * `MainActivity` used before - the home screen, generator, shop and friends destinations all need real
 * back-stack behaviour, which an enum plus a boolean cannot express.
 */
object Routes {

	const val HOME = "home"
	const val GENERATOR = "generator"
	const val ENTER_CODE = "enterCode"
	const val SHOP = "shop"
	const val STATS = "stats"
	const val SETTINGS = "settings"
	const val FRIENDS = "friends"

	const val ARG_MODE = "mode"
	const val ARG_SIZE = "size"
	const val ARG_VARIANT = "variant"
	const val ARG_DIFFICULTY = "difficulty"
	const val ARG_CODE = "code"
	const val ARG_MATCH_ID = "matchId"
	const val ARG_INVITE_TOKEN = "inviteToken"
	const val ARG_STAKE = "stake"
	const val ARG_PLAYER_ID = "playerId"

	/**
	 * One player's profile and statistics (friends item 2). Only the id travels in the route - the screen
	 * re-reads everything else, so a display name (user-supplied text that would have to be escaped) never
	 * has to be put in a URL.
	 */
	const val PLAYER_DETAIL = "player/{$ARG_PLAYER_ID}"

	fun playerDetail(playerId: String): String = "player/$playerId"

	/**
	 * Plain `multiplayer` is the match-setup screen. The optional arguments carry a match that already
	 * exists - one this player just created for a specific opponent, or one they were asked to join from
	 * the match-request overlay - so those paths skip setup entirely.
	 */
	const val MULTIPLAYER = "multiplayer?$ARG_MATCH_ID={$ARG_MATCH_ID}&$ARG_INVITE_TOKEN={$ARG_INVITE_TOKEN}&$ARG_MODE={$ARG_MODE}&$ARG_STAKE={$ARG_STAKE}"

	/**
	 * `mode` picks the save slot; the optional arguments carry what the generator or the share-code
	 * screen chose. Passing them through the route rather than a shared view model is deliberate: each
	 * `composable` gets its own Hilt view-model store, so a `GameViewModel` obtained on the generator
	 * screen would be a *different* instance from the one the play screen resolves.
	 */
	const val PLAY = "play/{$ARG_MODE}?$ARG_SIZE={$ARG_SIZE}&$ARG_VARIANT={$ARG_VARIANT}&$ARG_DIFFICULTY={$ARG_DIFFICULTY}&$ARG_CODE={$ARG_CODE}"

	fun play(mode: PlayMode): String = "play/${mode.name}"

	/** The match-setup screen, with nothing pre-selected. */
	fun multiplayer(): String = "multiplayer"

	/** A match this player created and has already asked someone to join - go straight into it. */
	fun multiplayerMatch(matchId: String, mode: String, stake: Int): String =
		"multiplayer?$ARG_MATCH_ID=$matchId&$ARG_MODE=$mode&$ARG_STAKE=$stake"

	/** A match request that was accepted: join with the token that came with it, then play. */
	fun multiplayerJoin(matchId: String, inviteToken: String): String =
		"multiplayer?$ARG_MATCH_ID=$matchId&$ARG_INVITE_TOKEN=$inviteToken"

	fun playGenerated(size: GridSize, variant: Variant, difficulty: Difficulty): String =
		"play/${PlayMode.NORMAL.name}?$ARG_SIZE=${size.name}&$ARG_VARIANT=${variant.name}&$ARG_DIFFICULTY=${difficulty.name}"

	fun playShareCode(code: String): String = "play/${PlayMode.NORMAL.name}?$ARG_CODE=$code"
}

enum class PlayMode {
	NORMAL, DAILY;

	companion object {
		fun fromArg(value: String?): PlayMode = entries.firstOrNull { it.name == value } ?: NORMAL
	}
}

/** What the play screen should do the first time it composes, derived from the route's arguments. */
sealed interface PlayRequest {

	/** Resume whatever is already in the slot - the plain `play/NORMAL` and `play/DAILY` cases. */
	data object Resume : PlayRequest

	data class Generate(val size: GridSize, val variant: Variant, val difficulty: Difficulty) : PlayRequest

	data class FromShareCode(val code: String) : PlayRequest

	companion object {

		fun of(size: String?, variant: String?, difficulty: String?, code: String?): PlayRequest {
			if (!code.isNullOrBlank()) return FromShareCode(code)
			if (size == null || variant == null || difficulty == null) return Resume
			// values(), not entries: these are Java enums from shared-core, matching every other call site.
			val parsedSize = GridSize.values().firstOrNull { it.name == size } ?: return Resume
			val parsedVariant = Variant.values().firstOrNull { it.name == variant } ?: return Resume
			val parsedDifficulty = Difficulty.values().firstOrNull { it.name == difficulty } ?: return Resume
			return Generate(parsedSize, parsedVariant, parsedDifficulty)
		}
	}
}
