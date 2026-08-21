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

	/**
	 * Settings item 2: connecting to a server, getting onto it and managing the account, as a workflow with
	 * named stages. Its own destination rather than a section of [SETTINGS] - it is a sequence, and a
	 * sequence buried under the language picker had nowhere to say which stage you were in.
	 */
	const val ACCOUNT = "settings/account"
	const val FRIENDS = "friends"

	const val ARG_TECHNIQUE = "technique"
	const val ARG_LEVEL = "level"
	const val ARG_SUB_LEVEL = "subLevel"
	const val ARG_EXAMPLE = "example"

	/**
	 * Game item 1 (2.1.0): whether the learn area was opened to be *worked through* or merely to be *looked
	 * something up in*.
	 *
	 * `true` is reference mode, which is what a running board opens. The wiki is a reasonable thing to reach
	 * for mid-puzzle - a hint has just named a technique, and the player wants to know what that is - but
	 * training is not: it is a second sudoku, started from inside the first one, on a screen the player still
	 * has a timed puzzle waiting behind. So reference mode keeps the descriptions and the worked examples and
	 * drops everything that is progress through the area: the training entry point, the mastery ring, and the
	 * per-technique progress lines that only mean something to someone who came here to train.
	 *
	 * Carried in the route rather than held in a view model because it has to survive process death with the
	 * back stack: a player who reads a wiki page while Android kills the app behind it comes back to the same
	 * page, and it must not have grown a "Start training" button in the meantime.
	 */
	const val ARG_REFERENCE = "reference"

	/**
	 * The technique wiki: every technique the shared core can teach, with the player's progress on each
	 * (learn item 2). The entry point of the learn area, and the only one reachable from the home screen -
	 * a technique's training is always entered through its wiki page, never directly, because the
	 * description is what the third training level leaves the player with.
	 */
	const val LEARN = "learn?$ARG_REFERENCE={$ARG_REFERENCE}"

	/**
	 * One technique explained: what it proves, how to spot it, worked examples, and the way into its
	 * training. The technique travels as its enum name, which is a fixed identifier rather than user text.
	 */
	const val LEARN_TECHNIQUE = "learn/{$ARG_TECHNIQUE}?$ARG_REFERENCE={$ARG_REFERENCE}"

	/**
	 * One worked example, on a screen of its own.
	 *
	 * Its own destination rather than a carousel on the wiki page: the page's dots said nothing about whether
	 * they meant the example or the step within it, and an example is a board plus an argument that runs over
	 * several beats, which is a thing to be looked at rather than a thing to be scrolled past.
	 */
	const val LEARN_EXAMPLE = "learn/{$ARG_TECHNIQUE}/example/{$ARG_EXAMPLE}"

	/** The three levels of a technique's training, with the sub-levels of each and their state. */
	const val LEARN_LEVELS = "learn/{$ARG_TECHNIQUE}/levels"

	/**
	 * Learn item 8: open this exercise on a position generated now, rather than on the one bundled with the
	 * app.
	 *
	 * A route argument because the choice is made *before* the exercise opens: it is offered on the level
	 * overview, where the player can see which exercise they are asking for a new puzzle for, instead of
	 * inside the exercise where it used to be, halfway through a board they had already started reading.
	 */
	const val ARG_FRESH = "fresh"

	/**
	 * One training puzzle. The level decides how much help is available, so it has to travel with the
	 * route rather than be looked up: a level 1 board and a level 3 board are the same screen under two
	 * different sets of rules.
	 */
	const val LEARN_TRAIN = "learn/{$ARG_TECHNIQUE}/train/{$ARG_LEVEL}/{$ARG_SUB_LEVEL}?$ARG_FRESH={$ARG_FRESH}"

	/**
	 * What the exercise about to open is asking for, on a screen of its own, before the board appears.
	 *
	 * Its own destination rather than a card above the board. The brief and the puzzle are two different
	 * things to do: one is read once, the other is worked at, and stacked on one screen the brief is what the
	 * player scrolls past to reach the board and then never reads. Separating them also lets the brief be
	 * turned off per level, which a card in the middle of a screen could not honestly offer.
	 *
	 * It carries the fresh-puzzle request through untouched, so asking for a new position on the overview
	 * still means the board that opens after the brief is the generated one.
	 */
	const val LEARN_BRIEF = "learn/{$ARG_TECHNIQUE}/brief/{$ARG_LEVEL}/{$ARG_SUB_LEVEL}?$ARG_FRESH={$ARG_FRESH}"

	/**
	 * The learn area's own settings, a sub screen of [SETTINGS] the way [ACCOUNT] is.
	 *
	 * It exists because the briefing can be dismissed for good from inside the training, and a choice made
	 * with one tap on the way past has to be undoable somewhere the player can find later.
	 */
	const val LEARN_SETTINGS = "settings/learn"

	fun learn(reference: Boolean = false): String = "learn?$ARG_REFERENCE=$reference"

	fun learnTechnique(technique: String, reference: Boolean = false): String =
		"learn/$technique?$ARG_REFERENCE=$reference"

	fun learnExample(technique: String, index: Int): String = "learn/$technique/example/$index"

	fun learnLevels(technique: String): String = "learn/$technique/levels"

	fun learnTrain(technique: String, level: Int, subLevel: Int, fresh: Boolean = false): String =
		"learn/$technique/train/$level/$subLevel?$ARG_FRESH=$fresh"

	fun learnBrief(technique: String, level: Int, subLevel: Int, fresh: Boolean = false): String =
		"learn/$technique/brief/$level/$subLevel?$ARG_FRESH=$fresh"

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
	 * The multiplayer entry point: create a game, or join one (multiplayer item 2). Two destinations, not a
	 * single screen with both halves stacked - creating and joining have nothing in common but the word
	 * multiplayer, and the combined screen made the join fields look like part of the create form.
	 */
	const val MULTIPLAYER_HUB = "multiplayer/hub"
	const val MULTIPLAYER_CREATE = "multiplayer/create"
	const val MULTIPLAYER_JOIN = "multiplayer/join"

	/**
	 * The lobby of a match that exists but has nobody in it yet (multiplayer item 4). Everything it needs
	 * travels in the route, so it survives process death without a shared view model: the token is what the
	 * creator is there to hand out, and the mode and stake are what the running match is entered with.
	 */
	const val MULTIPLAYER_WAIT =
		"multiplayer/wait/{$ARG_MATCH_ID}?$ARG_INVITE_TOKEN={$ARG_INVITE_TOKEN}&$ARG_MODE={$ARG_MODE}&$ARG_STAKE={$ARG_STAKE}"

	fun multiplayerWait(matchId: String, inviteToken: String, mode: String, stake: Int): String =
		"multiplayer/wait/$matchId?$ARG_INVITE_TOKEN=$inviteToken&$ARG_MODE=$mode&$ARG_STAKE=$stake"

	/**
	 * A match being played. The arguments carry which one: a match this player created and somebody has now
	 * joined, or one they were asked to join from the match-request overlay - the latter still needs the
	 * token, since it has not been joined yet.
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

	/** A match somebody has joined - go straight into it. */
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
