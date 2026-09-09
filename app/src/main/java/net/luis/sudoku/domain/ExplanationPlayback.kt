package net.luis.sudoku.domain

import net.luis.sudoku.solver.CellRole
import net.luis.sudoku.solver.Explanation
import net.luis.sudoku.solver.PatternCell
import net.luis.sudoku.solver.StepKind
import net.luis.sudoku.solver.UnitRef

/**
 * Everything one beat of an explanation asks the board to draw.
 *
 * In `domain` rather than beside the lesson board it was written for: it is pure translation from the core's
 * [Explanation] into what a renderer needs, with no Compose in it, and the *play* board reads it too - a hint
 * that shows the technique is the same explanation drawn on a different board (see [HintStep]).
 *
 * A frame is cumulative: it holds what this step adds *and* everything the steps before it showed, because
 * that is how a pattern assembles itself in front of a player. A step that replaced what came before would
 * leave a fish with four corners nobody ever saw at once.
 *
 * How it is drawn follows one picture, the owner's reference diagram: the cells of the pattern are filled in
 * a handful of colours ([DiagramTone]), the links of the argument are lines between the candidates they join,
 * the candidates it removes are crossed out, and the cell it solves is filled green with its digit written in.
 * A key under the board says what each colour means ([legendOf]).
 *
 * @param roles which part each cell plays. A cell the conclusion names keeps the part the pattern gave it,
 *   and only a cell the pattern never named is [CellRole.TARGET]
 * @param digits the candidates that matter in a cell, as the core's bitmask, so everything else can be dimmed
 * @param units the rows, columns and regions to outline
 * @param focusDigit the digit the whole argument is about, or 0 when it is not about one
 * @param struck the candidates being removed, drawn crossed out rather than simply gone: the removal is the
 *   thing being taught, and a candidate that has already vanished teaches nothing
 * @param placement the cell and digit written into the board, when the frame writes one
 */
data class ExplanationFrame(
	val roles: Map<Int, CellRole> = emptyMap(),
	val digits: Map<Int, Int> = emptyMap(),
	val units: List<UnitRef> = emptyList(),
	val focusDigit: Int = 0,
	val struck: Map<Int, Int> = emptyMap(),
	val placement: Pair<Int, Int>? = null,
	/** What this particular step is saying, which is what the caption under the board renders. */
	val kind: StepKind = StepKind.PATTERN,
	/** The digit this step is about, which is not always the digit the whole argument is about. */
	val stepDigit: Int = 0,
	/**
	 * Learn item 10: the cells *this* step names, as opposed to [roles], which is everything named so far.
	 *
	 * A frame has to be both cumulative and pointed. Cumulative, because a fish with four corners nobody ever
	 * saw at once teaches nothing; pointed, because by the fifth step a third of the board is coloured in and
	 * "these cells" no longer picks anything out. The board draws the earlier cells faded and these at full
	 * strength, so the argument stays on screen while the sentence still has a referent.
	 *
	 * Empty means "no current step": a frame being shown as a summary rather than as a beat, which is what the
	 * example tiles, a finished exercise and a hint draw. Everything is then at full strength.
	 */
	val currentCells: List<Int> = emptyList(),
	/** The units this step names, for the same reason and drawn the same way. */
	val currentUnits: List<UnitRef> = emptyList(),
	/** The candidates this step names per cell, as the core's bitmask, for the caption's detail line. */
	val currentDigits: Map<Int, Int> = emptyMap(),
	/** Every link drawn so far, as lines between the candidates they join. */
	val links: List<DiagramLink> = emptyList(),
	/** The links this step adds, drawn at full strength against the earlier ones, like [currentCells]. */
	val currentLinks: List<DiagramLink> = emptyList(),
	/**
	 * The cell the argument ends up solving, filled green, with its digit when the frame may name it.
	 *
	 * Separate from [placement] because the two are not the same promise: a hint marks the cell before it
	 * has been paid for, and so marks it without the digit (0), while [placement] is a digit on the board.
	 */
	val target: Pair<Int, Int>? = null
) {

	/**
	 * The colour group [cell] is filled in, or `null` for a cell the frame does not colour.
	 *
	 * The solved cell outranks everything, then the part the pattern gave a cell, and only a cell the pattern
	 * never named is filled for what the conclusion does to it. A naked pair's own cells lose candidates as
	 * well, and filling them red would hide the pair the elimination is argued from.
	 */
	fun toneOf(cell: Int): DiagramTone? {
		if (this.placement?.first == cell || this.target?.first == cell) {
			return DiagramTone.TARGET
		}
		val role = this.roles[cell]
		return when {
			role == null -> if ((this.struck[cell] ?: 0) != 0) DiagramTone.ELIMINATED else null
			role == CellRole.TARGET -> if ((this.struck[cell] ?: 0) != 0) DiagramTone.ELIMINATED else DiagramTone.TARGET
			else -> toneOf(role)
		}
	}
}

/**
 * The colours a pattern is drawn in: few enough that a key under the board can name every one of them.
 *
 * The core has twelve roles, and a board that gave each its own fill needed a legend nobody reads. What a
 * player has to tell apart is much less than that - the pattern, the second set the pattern is played against,
 * what only supports it, what goes, and what is solved - so the roles are folded onto those five.
 */
enum class DiagramTone {
	PATTERN,
	SECONDARY,
	CONTEXT,
	ELIMINATED,
	TARGET
}

/**
 * Which colour group a role is drawn in.
 *
 * The pairs a technique has to show apart land on opposite sides: a fish's base against its fin, an ALS against
 * its partner, a wing's pivot against its wings, a deadly pattern's floor against its roof.
 */
fun toneOf(role: CellRole): DiagramTone = when (role) {
	CellRole.PATTERN, CellRole.BASE, CellRole.LINK_ON, CellRole.LINK_OFF, CellRole.WING, CellRole.FLOOR -> DiagramTone.PATTERN
	CellRole.COVER, CellRole.PIVOT, CellRole.FIN, CellRole.ROOF -> DiagramTone.SECONDARY
	CellRole.CONTEXT -> DiagramTone.CONTEXT
	CellRole.TARGET -> DiagramTone.ELIMINATED
}

/**
 * One line of the diagram, from a candidate in one cell to a candidate in another.
 *
 * An end is a list of cells because a grouped chain's node is several cells acting as one candidate; the line
 * then starts from the middle of the group. A digit of 0 anchors the end on the middle of the cell instead of
 * on the candidate's slot, for a link the core did not tie to one digit.
 *
 * @param strong a strong link ("if not here, then there"), drawn solid; a weak one is drawn dashed
 */
data class DiagramLink(
	val from: List<Int>,
	val fromDigit: Int,
	val to: List<Int>,
	val toDigit: Int,
	val strong: Boolean
)

/**
 * One row of the key under the board: a colour and what it stands for here.
 *
 * @param label which wording the row uses, chosen by the role the colour was first given
 * @param digits the digits the row is about, as the core's bitmask, 0 when it is not about one
 */
data class LegendEntry(val tone: DiagramTone, val label: LegendLabel, val digits: Int = 0)

enum class LegendLabel {
	CHAIN,
	PATTERN,
	BASE,
	COVER,
	PIVOT,
	WINGS,
	FIN,
	FLOOR,
	ROOF,
	CONTEXT,
	ELIMINATED,
	TARGET
}

/**
 * The key for [frame]: one row per colour the board is actually showing, in the order the argument reads.
 *
 * A colour is named by the first role that put it on the board, so an X-Chain's blue says "chain" and a
 * fish's says "base". The digits are added when every cell of that colour is about one digit, which is what
 * turns "chain" into "chain (9)".
 */
fun legendOf(frame: ExplanationFrame): List<LegendEntry> {
	val firstRole = linkedMapOf<DiagramTone, CellRole>()
	val digits = mutableMapOf<DiagramTone, Int>()
	val cells = (frame.roles.keys + frame.struck.keys + listOfNotNull(frame.placement?.first, frame.target?.first)).toSortedSet()
	for (cell in cells) {
		val tone = frame.toneOf(cell) ?: continue
		frame.roles[cell]?.let { role -> if (toneOf(role) == tone) firstRole.putIfAbsent(tone, role) }
		val mask = when (tone) {
			DiagramTone.ELIMINATED -> frame.struck[cell] ?: 0
			DiagramTone.TARGET -> 0
			else -> frame.digits[cell] ?: 0
		}
		digits[tone] = (digits[tone] ?: 0) or mask
	}
	// Struck candidates in a cell that keeps a pattern colour still need their row in the key.
	// A cell the frame has already written its digit into shows none of its candidates, struck ones included.
	val struckMask = frame.struck.filterKeys { cell -> cell != frame.placement?.first }.values.fold(0) { mask, value -> mask or value }

	val entries = mutableListOf<LegendEntry>()
	for (tone in DiagramTone.entries) {
		when (tone) {
			DiagramTone.ELIMINATED -> if (struckMask != 0) {
				entries.add(LegendEntry(tone, LegendLabel.ELIMINATED, struckMask))
			}
			DiagramTone.TARGET -> {
				val digit = frame.placement?.second ?: frame.target?.second ?: 0
				if (frame.placement != null || frame.target != null) {
					entries.add(LegendEntry(tone, LegendLabel.TARGET, if (digit > 0) 1 shl digit else 0))
				}
			}
			else -> if (tone in digits) {
				val mask = digits[tone] ?: 0
				entries.add(LegendEntry(tone, labelOf(firstRole[tone] ?: CellRole.PATTERN), mask.takeIf { Integer.bitCount(it) == 1 } ?: 0))
			}
		}
	}
	return entries
}

private fun labelOf(role: CellRole): LegendLabel = when (role) {
	CellRole.LINK_ON, CellRole.LINK_OFF -> LegendLabel.CHAIN
	CellRole.PATTERN -> LegendLabel.PATTERN
	CellRole.BASE -> LegendLabel.BASE
	CellRole.COVER -> LegendLabel.COVER
	CellRole.PIVOT -> LegendLabel.PIVOT
	CellRole.WING -> LegendLabel.WINGS
	CellRole.FIN -> LegendLabel.FIN
	CellRole.FLOOR -> LegendLabel.FLOOR
	CellRole.ROOF -> LegendLabel.ROOF
	CellRole.CONTEXT -> LegendLabel.CONTEXT
	CellRole.TARGET -> LegendLabel.ELIMINATED
}

/**
 * Turns an explanation into the frames a player steps through, one per beat.
 *
 * The conclusion is a beat like any other rather than a special case at the end: the eliminations it names
 * are drawn struck through in place, so the player sees *which* candidates go rather than watching them
 * disappear and having to remember what used to be there.
 *
 * @param isPeer whether two cells share a row, column or region, which is what pairs a W-Wing's two cells
 *   with the ends of its link; the learn area's classic 9x9 by default
 * @param target the cell and digit the argument finally solves. When the explanation stops at an elimination,
 *   one more beat is added that writes it in, so the lesson ends on the cell it was for
 */
fun framesOf(
	explanation: Explanation,
	isPeer: (Int, Int) -> Boolean = ::isClassicPeer,
	target: Pair<Int, Int>? = null
): List<ExplanationFrame> {
	val frames = mutableListOf<ExplanationFrame>()
	val roles = mutableMapOf<Int, CellRole>()
	val digits = mutableMapOf<Int, Int>()
	val units = mutableListOf<UnitRef>()
	val struck = mutableMapOf<Int, Int>()
	val links = mutableListOf<DiagramLink>()
	val linker = Linker(isPeer)
	var focusDigit = 0
	var placement: Pair<Int, Int>? = null

	for (step in explanation.steps()) {
		if (step.kind() == StepKind.FOCUS_DIGIT) {
			focusDigit = step.digit()
		}
		val conclusion = step.kind() == StepKind.ELIMINATION || step.kind() == StepKind.PLACEMENT
		val currentCells = mutableListOf<Int>()
		val currentDigits = mutableMapOf<Int, Int>()
		for (unit in step.units()) {
			if (unit !in units) {
				units.add(unit)
			}
		}
		for (cell in step.cells()) {
			currentCells.add(cell.cell())
			currentDigits[cell.cell()] = (currentDigits[cell.cell()] ?: 0) or cell.digits()
			if (conclusion || cell.role() == CellRole.TARGET) {
				// The part a cell played in the pattern is kept: a naked pair loses candidates from its own
				// cells, and the conclusion repainting them would take the pair off the board. A hidden pair
				// names its own cells as targets already in its implication beat, before any conclusion.
				roles.putIfAbsent(cell.cell(), cell.role())
			} else {
				roles[cell.cell()] = cell.role()
			}
			// Merged rather than replaced: a cell that carries two of the pattern's digits, as the cells of a
			// naked pair do, is about both of them at once.
			digits[cell.cell()] = (digits[cell.cell()] ?: 0) or cell.digits()
			if (step.kind() == StepKind.ELIMINATION) {
				struck[cell.cell()] = (struck[cell.cell()] ?: 0) or cell.digits()
			}
			if (step.kind() == StepKind.PLACEMENT) {
				placement = cell.cell() to step.digit()
			}
		}
		val currentLinks = linker.linksOf(step.kind(), step.cells())
		links.addAll(currentLinks)

		frames.add(
			ExplanationFrame(
				roles = roles.toMap(),
				digits = digits.toMap(),
				units = units.toList(),
				focusDigit = focusDigit,
				struck = struck.toMap(),
				placement = placement,
				kind = step.kind(),
				stepDigit = step.digit(),
				currentCells = currentCells.toList(),
				currentUnits = step.units().toList(),
				currentDigits = currentDigits.toMap(),
				links = links.toList(),
				currentLinks = currentLinks
			)
		)
	}

	val closing = linker.close()
	if (closing.isNotEmpty() && frames.isNotEmpty()) {
		// A W-Wing's last line is only known once its link is: it goes onto the beat that drew the link.
		val index = frames.indexOfLast { it.kind == StepKind.LINK }.takeIf { it >= 0 } ?: frames.lastIndex
		for (at in index until frames.size) {
			val frame = frames[at]
			frames[at] = frame.copy(
				links = frame.links + closing,
				currentLinks = if (at == index) frame.currentLinks + closing else frame.currentLinks
			)
		}
	}

	val last = frames.lastOrNull()
	if (target != null && target.second > 0 && last != null && last.placement == null) {
		frames.add(
			last.copy(
				placement = target,
				kind = StepKind.PLACEMENT,
				stepDigit = target.second,
				currentCells = listOf(target.first),
				currentUnits = emptyList(),
				currentDigits = mapOf(target.first to (1 shl target.second)),
				currentLinks = emptyList()
			)
		)
	}
	return frames
}

/**
 * Works out the lines of a diagram from the beats, since the core records links as cells and roles only.
 *
 * Three shapes are read, and together they are every chain and wing the core explains with a link beat:
 * - a link beat with an assumed-false and an assumed-true end is a **strong** link between the two, and the
 *   bridge from the previous link's true end to this one's false end is the **weak** link between them - an
 *   X-Chain, a Skyscraper, a Kite, an AIC;
 * - a W-Wing's first beat names its two bivalue cells, and each is joined weakly to the end of the link it sees;
 * - a link beat whose cells carry no ends at all follows a beat of one cell, the pivot of an XY- or XYZ-Wing,
 *   which is joined weakly to each wing on the digit the two share.
 */
private class Linker(private val isPeer: (Int, Int) -> Boolean) {

	private var previousOn: List<PatternCell> = emptyList()
	private var previousCells: List<PatternCell> = emptyList()
	private var wingPair: List<PatternCell> = emptyList()
	private var closingEnd: List<PatternCell> = emptyList()
	private var pairedFirst: PatternCell? = null

	fun linksOf(kind: StepKind, cells: List<PatternCell>): List<DiagramLink> {
		val links = mutableListOf<DiagramLink>()
		if (kind == StepKind.PATTERN && cells.size == 2 && cells.all { it.role() == CellRole.PATTERN }) {
			this.wingPair = cells
		}
		if (kind == StepKind.LINK) {
			val off = cells.filter { it.role() == CellRole.LINK_OFF }
			val on = cells.filter { it.role() == CellRole.LINK_ON }
			if (off.isNotEmpty() && on.isNotEmpty()) {
				if (this.previousOn.isNotEmpty() && this.previousOn.map { it.cell() } != off.map { it.cell() }) {
					links.add(link(this.previousOn, off, strong = false))
				} else if (this.previousOn.isEmpty() && this.wingPair.isNotEmpty()) {
					val first = this.wingPair.firstOrNull { pair -> off.any { isPeer(pair.cell(), it.cell()) } }
					if (first != null) {
						links.add(link(listOf(first), off, strong = false, fromDigit = singleDigit(off)))
						this.pairedFirst = first
					}
				}
				links.add(link(off, on, strong = true))
				this.previousOn = on
				this.closingEnd = on
			} else if (this.previousCells.size == 1 && cells.isNotEmpty() && cells.all { it.role() == CellRole.WING }) {
				val pivot = this.previousCells.single()
				for (wing in cells) {
					val shared = pivot.digits() and wing.digits()
					val digit = if (Integer.bitCount(shared) == 1) Integer.numberOfTrailingZeros(shared) else 0
					links.add(DiagramLink(listOf(pivot.cell()), digit, listOf(wing.cell()), digit, strong = false))
				}
			}
		}
		if (cells.isNotEmpty() && kind != StepKind.IMPLICATION) {
			this.previousCells = cells
		}
		return links
	}

	/** The W-Wing's second weak link, from the far end of its link to the other bivalue cell. */
	fun close(): List<DiagramLink> {
		val first = this.pairedFirst ?: return emptyList()
		val second = this.wingPair.firstOrNull { it.cell() != first.cell() } ?: return emptyList()
		if (this.closingEnd.none { isPeer(it.cell(), second.cell()) }) {
			return emptyList()
		}
		return listOf(link(this.closingEnd, listOf(second), strong = false, toDigit = singleDigit(this.closingEnd)))
	}

	private fun link(
		from: List<PatternCell>,
		to: List<PatternCell>,
		strong: Boolean,
		fromDigit: Int = singleDigit(from),
		toDigit: Int = singleDigit(to)
	): DiagramLink = DiagramLink(from.map { it.cell() }, fromDigit, to.map { it.cell() }, toDigit, strong)

	private fun singleDigit(cells: List<PatternCell>): Int {
		val mask = cells.fold(0) { mask, cell -> mask or cell.digits() }
		return if (Integer.bitCount(mask) == 1) Integer.numberOfTrailingZeros(mask) else 0
	}
}

/** Whether two cells of a classic 9x9 share a row, a column or a box, which is all the learn area plays. */
fun isClassicPeer(first: Int, second: Int): Boolean {
	if (first == second) return false
	if (first / 9 == second / 9 || first % 9 == second % 9) return true
	return (first / 27) * 3 + (first % 9) / 3 == (second / 27) * 3 + (second % 9) / 3
}
