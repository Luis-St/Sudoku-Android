package net.luis.sudoku.ui.learn

import androidx.annotation.StringRes
import net.luis.sudoku.R
import net.luis.sudoku.solver.Technique

/**
 * The three pieces of copy every technique has: its name, what it proves, and how to spot it.
 *
 * The mapping is written out rather than resolved from the enum name at runtime. `getIdentifier` would turn
 * every one of these strings into a resource the shrinker cannot see is used, and it would fail on a device
 * rather than at build time when a technique is added and its copy is not. A `when` over the enum is
 * checked, so the compiler is what notices.
 */
data class LearnStrings(
	@get:StringRes val name: Int,
	@get:StringRes val description: Int,
	@get:StringRes val pattern: Int
)

/**
 * The copy for one technique.
 *
 * Every technique the learn area teaches has an entry. The ones it does not teach have none, and asking for
 * one is a programming error rather than something to render around - inside the learn area, where the list
 * of techniques *is* [net.luis.sudoku.learn.LearnTechniques.taught]. A caller that names a technique the
 * player's board chose rather than one the learn area listed asks [stringsOrNull] instead.
 */
fun stringsOf(technique: Technique): LearnStrings = stringsOrNull(technique)
	?: throw IllegalArgumentException("The learn area does not teach $technique")

/**
 * The same copy, or `null` for a technique the learn area does not teach (issue 2.2.2/2).
 *
 * A hint runs on whatever the solver needed, which is not the taught set: the five level-15 dynamic
 * techniques are deferred, and `LAW_OF_LEFTOVERS` and `MULTI_COLOURING` were dropped. Every one of them can
 * still be the technique a hint names on a hard enough board, and asking [stringsOf] for its name there took
 * the game screen down with an `IllegalArgumentException` - a hint is not a place to insist the copy exists.
 */
fun stringsOrNull(technique: Technique): LearnStrings? = when (technique) {
	Technique.FULL_HOUSE -> LearnStrings(R.string.learn_technique_full_house_name, R.string.learn_technique_full_house_description, R.string.learn_technique_full_house_pattern)
	Technique.LAST_DIGIT -> LearnStrings(R.string.learn_technique_last_digit_name, R.string.learn_technique_last_digit_description, R.string.learn_technique_last_digit_pattern)
	Technique.NAKED_SINGLE -> LearnStrings(R.string.learn_technique_naked_single_name, R.string.learn_technique_naked_single_description, R.string.learn_technique_naked_single_pattern)
	Technique.HIDDEN_SINGLE_REGION -> LearnStrings(R.string.learn_technique_hidden_single_region_name, R.string.learn_technique_hidden_single_region_description, R.string.learn_technique_hidden_single_region_pattern)
	Technique.HIDDEN_SINGLE_LINE -> LearnStrings(R.string.learn_technique_hidden_single_line_name, R.string.learn_technique_hidden_single_line_description, R.string.learn_technique_hidden_single_line_pattern)
	Technique.POINTING -> LearnStrings(R.string.learn_technique_pointing_name, R.string.learn_technique_pointing_description, R.string.learn_technique_pointing_pattern)
	Technique.CLAIMING -> LearnStrings(R.string.learn_technique_claiming_name, R.string.learn_technique_claiming_description, R.string.learn_technique_claiming_pattern)
	Technique.NAKED_PAIR -> LearnStrings(R.string.learn_technique_naked_pair_name, R.string.learn_technique_naked_pair_description, R.string.learn_technique_naked_pair_pattern)
	Technique.HIDDEN_PAIR -> LearnStrings(R.string.learn_technique_hidden_pair_name, R.string.learn_technique_hidden_pair_description, R.string.learn_technique_hidden_pair_pattern)
	Technique.NAKED_TRIPLE -> LearnStrings(R.string.learn_technique_naked_triple_name, R.string.learn_technique_naked_triple_description, R.string.learn_technique_naked_triple_pattern)
	Technique.HIDDEN_TRIPLE -> LearnStrings(R.string.learn_technique_hidden_triple_name, R.string.learn_technique_hidden_triple_description, R.string.learn_technique_hidden_triple_pattern)
	Technique.X_WING -> LearnStrings(R.string.learn_technique_x_wing_name, R.string.learn_technique_x_wing_description, R.string.learn_technique_x_wing_pattern)
	Technique.SKYSCRAPER -> LearnStrings(R.string.learn_technique_skyscraper_name, R.string.learn_technique_skyscraper_description, R.string.learn_technique_skyscraper_pattern)
	Technique.TWO_STRING_KITE -> LearnStrings(R.string.learn_technique_two_string_kite_name, R.string.learn_technique_two_string_kite_description, R.string.learn_technique_two_string_kite_pattern)
	Technique.SWORDFISH -> LearnStrings(R.string.learn_technique_swordfish_name, R.string.learn_technique_swordfish_description, R.string.learn_technique_swordfish_pattern)
	Technique.BUG_PLUS_ONE -> LearnStrings(R.string.learn_technique_bug_plus_one_name, R.string.learn_technique_bug_plus_one_description, R.string.learn_technique_bug_plus_one_pattern)
	Technique.CRANE -> LearnStrings(R.string.learn_technique_crane_name, R.string.learn_technique_crane_description, R.string.learn_technique_crane_pattern)
	Technique.XY_WING -> LearnStrings(R.string.learn_technique_xy_wing_name, R.string.learn_technique_xy_wing_description, R.string.learn_technique_xy_wing_pattern)
	Technique.UNIQUE_RECTANGLE_1 -> LearnStrings(R.string.learn_technique_unique_rectangle_1_name, R.string.learn_technique_unique_rectangle_1_description, R.string.learn_technique_unique_rectangle_1_pattern)
	Technique.UNIQUE_RECTANGLE_2 -> LearnStrings(R.string.learn_technique_unique_rectangle_2_name, R.string.learn_technique_unique_rectangle_2_description, R.string.learn_technique_unique_rectangle_2_pattern)
	Technique.XYZ_WING -> LearnStrings(R.string.learn_technique_xyz_wing_name, R.string.learn_technique_xyz_wing_description, R.string.learn_technique_xyz_wing_pattern)
	Technique.W_WING -> LearnStrings(R.string.learn_technique_w_wing_name, R.string.learn_technique_w_wing_description, R.string.learn_technique_w_wing_pattern)
	Technique.FINNED_X_WING -> LearnStrings(R.string.learn_technique_finned_x_wing_name, R.string.learn_technique_finned_x_wing_description, R.string.learn_technique_finned_x_wing_pattern)
	Technique.NAKED_QUAD -> LearnStrings(R.string.learn_technique_naked_quad_name, R.string.learn_technique_naked_quad_description, R.string.learn_technique_naked_quad_pattern)
	Technique.HIDDEN_QUAD -> LearnStrings(R.string.learn_technique_hidden_quad_name, R.string.learn_technique_hidden_quad_description, R.string.learn_technique_hidden_quad_pattern)
	Technique.UNIQUE_RECTANGLE_3 -> LearnStrings(R.string.learn_technique_unique_rectangle_3_name, R.string.learn_technique_unique_rectangle_3_description, R.string.learn_technique_unique_rectangle_3_pattern)
	Technique.UNIQUE_RECTANGLE_4 -> LearnStrings(R.string.learn_technique_unique_rectangle_4_name, R.string.learn_technique_unique_rectangle_4_description, R.string.learn_technique_unique_rectangle_4_pattern)
	Technique.EMPTY_RECTANGLE -> LearnStrings(R.string.learn_technique_empty_rectangle_name, R.string.learn_technique_empty_rectangle_description, R.string.learn_technique_empty_rectangle_pattern)
	Technique.FINNED_SWORDFISH -> LearnStrings(R.string.learn_technique_finned_swordfish_name, R.string.learn_technique_finned_swordfish_description, R.string.learn_technique_finned_swordfish_pattern)
	Technique.SASHIMI_SWORDFISH -> LearnStrings(R.string.learn_technique_sashimi_swordfish_name, R.string.learn_technique_sashimi_swordfish_description, R.string.learn_technique_sashimi_swordfish_pattern)
	Technique.JELLYFISH -> LearnStrings(R.string.learn_technique_jellyfish_name, R.string.learn_technique_jellyfish_description, R.string.learn_technique_jellyfish_pattern)
	Technique.SIMPLE_COLOURING -> LearnStrings(R.string.learn_technique_simple_colouring_name, R.string.learn_technique_simple_colouring_description, R.string.learn_technique_simple_colouring_pattern)
	Technique.WXYZ_WING -> LearnStrings(R.string.learn_technique_wxyz_wing_name, R.string.learn_technique_wxyz_wing_description, R.string.learn_technique_wxyz_wing_pattern)
	Technique.X_CHAIN -> LearnStrings(R.string.learn_technique_x_chain_name, R.string.learn_technique_x_chain_description, R.string.learn_technique_x_chain_pattern)
	Technique.XY_CHAIN -> LearnStrings(R.string.learn_technique_xy_chain_name, R.string.learn_technique_xy_chain_description, R.string.learn_technique_xy_chain_pattern)
	Technique.AIC -> LearnStrings(R.string.learn_technique_aic_name, R.string.learn_technique_aic_description, R.string.learn_technique_aic_pattern)
	Technique.ALS_XZ -> LearnStrings(R.string.learn_technique_als_xz_name, R.string.learn_technique_als_xz_description, R.string.learn_technique_als_xz_pattern)
	Technique.SUE_DE_COQ -> LearnStrings(R.string.learn_technique_sue_de_coq_name, R.string.learn_technique_sue_de_coq_description, R.string.learn_technique_sue_de_coq_pattern)
	Technique.MEDUSA_3D -> LearnStrings(R.string.learn_technique_medusa_3d_name, R.string.learn_technique_medusa_3d_description, R.string.learn_technique_medusa_3d_pattern)
	Technique.GROUPED_AIC -> LearnStrings(R.string.learn_technique_grouped_aic_name, R.string.learn_technique_grouped_aic_description, R.string.learn_technique_grouped_aic_pattern)
	Technique.ALS_CHAIN -> LearnStrings(R.string.learn_technique_als_chain_name, R.string.learn_technique_als_chain_description, R.string.learn_technique_als_chain_pattern)
	else -> null
}
