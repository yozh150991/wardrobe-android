package com.familywardrobe.app

import java.time.Instant
import java.time.ZoneId

/**
 * ROT-01: today's outfit around an item that has not been worn for a long time, with why it is suggested.
 * [look] is empty only for an item the person chose themselves ([RotationOutfits.suggest] with an anchor) that cannot
 * be worn today; [problem] then says why (a [RotationRules] code), or is null when other items are missing.
 */
data class RotationLook(val anchor: Garment, val look: List<Garment>, val lastWear: String?, val added: String,
    val idleDays: Long, val forgotten: Boolean, val others: Int, val problem: String? = null)

object RotationOutfits {
    /** The day an item was added, in the phone's time zone (as the statistics count it). */
    fun added(g: Garment): String = Instant.ofEpochMilli(g.createdAt).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    /** The last actual wear of each item on or before [today]; plans count only once they are marked as worn. */
    fun lastWears(s: Snapshot, today: String): Map<String, String> =
        s.wears.filter { it.date <= today }.groupBy { it.garmentId }.mapValues { (_, rows) -> rows.maxOf { it.date } }

    /** [state]: ROT-02 postponements and which items the card showed recently. */
    fun items(s: Snapshot, wardrobe: String, today: String, state: RotationState = RotationState()): List<RotationRules.Item> {
        val last = lastWears(s, today)
        // PERS-01 «Незручно» is an item row: a signature of one ID.
        val uncomfortable = s.feedback.filter { it.wardrobeId == wardrobe && !it.signature.contains('|') && it.reason == PreferenceRules.DISLIKE }
            .map { it.signature }.toSet()
        return s.garments.filter { it.wardrobeId == wardrobe }.map { g ->
            RotationRules.Item(g.rulePiece(), last[g.id].orEmpty(), added(g), g.suggestionExcluded, g.id in uncomfortable,
                state.snoozes[g.id]?.until.orEmpty(), state.seen[g.id].orEmpty(), g.rarelyWorn)
        }
    }

    /** Items not worn for 90 days that suit today, in the order they are suggested. */
    fun candidates(s: Snapshot, wardrobe: String, occasion: String, weather: String, today: String,
        choices: PersonalChoices = PersonalChoices(), state: RotationState = RotationState(), temperature: Double? = null): List<String> =
        RotationRules.candidates(items(s, wardrobe, today, state), wardrobe, weather, DailyOutfits.style(s, wardrobe, occasion, choices).style, today, temperature)

    /**
     * The first candidate after [after] (wrapping around) that makes a whole outfit for today, keeping the person's
     * pinned items ([fixed]) and hidden combinations. With [anchor], only that item is tried, forgotten, postponed or not.
     */
    fun suggest(s: Snapshot, wardrobe: String, occasion: String, weather: String, today: String,
        after: String = "", anchor: String = "", fixed: Map<String, String> = emptyMap(),
        choices: PersonalChoices = PersonalChoices(), state: RotationState = RotationState(), temperature: Double? = null): RotationLook? {
        val byId = s.garments.filter { it.wardrobeId == wardrobe }.associateBy { it.id }
        val items = items(s, wardrobe, today, state).associateBy { it.piece.id }
        val candidates = candidates(s, wardrobe, occasion, weather, today, choices, state, temperature)
        fun result(g: Garment, look: List<Garment>, problem: String? = null): RotationLook {
            val item = items.getValue(g.id)
            return RotationLook(g, look, item.lastWear.ifEmpty { null }, item.added, RotationRules.idleDays(item.lastWear, item.added, today),
                RotationRules.forgotten(item.lastWear, item.added, today), (candidates - g.id).size, problem)
        }
        fun outfit(g: Garment, pins: Map<String, String>): List<Garment>? =
            DailyOutfits.generate(s, wardrobe, occasion, weather, today, "rotation:$today",
                fixed = pins + (g.category to g.id), choices = choices, temperature = temperature).firstOrNull()
        if (anchor.isNotEmpty()) {
            val g = byId[anchor] ?: return null
            val item = items.getValue(g.id)
            // Chosen by the person: «Незручно» does not stop it, and it replaces a pin of its own category.
            val problem = RotationRules.problem(item, weather, temperature)?.takeIf { it != RotationRules.UNCOMFORTABLE }
                ?: RotationRules.REPEAT.takeIf { !RecommendationRules.allowed(false, item.lastWear, today,
                    s.wardrobes.find { it.id == wardrobe }?.noRepeatDays ?: 0) }
            return result(g, if (problem == null) outfit(g, fixed.filterKeys { it != g.category }).orEmpty() else emptyList(), problem)
        }
        for (id in RotationRules.order(candidates, after).take(RotationRules.ATTEMPTS)) {
            val g = byId[id] ?: continue
            // A pinned item of the same category wins: the person chose it for today.
            if (fixed[g.category]?.let { it != g.id } == true) continue
            outfit(g, fixed)?.let { return result(g, it) }
        }
        return null
    }
}
