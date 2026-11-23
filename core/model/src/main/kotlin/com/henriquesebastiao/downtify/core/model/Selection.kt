package com.henriquesebastiao.downtify.core.model

/**
 * What the user picked for a bulk action, in the order they picked it.
 *
 * Pure value: every operation returns a new [Selection], so a ViewModel keeps the current one in a
 * `StateFlow` and the screen is a function of it. Ids are media ids: podcast episodes
 * ([EpisodeIds.isEpisode]) are never offered, and [playlistTrackIds] drops them anyway.
 */
data class Selection(val ids: List<String> = emptyList()) {
    val count: Int get() = ids.size

    val isEmpty: Boolean get() = ids.isEmpty()

    fun contains(id: String): Boolean = id in ids

    /** Picks [id], or unpicks it when it was picked. */
    fun toggle(id: String): Selection = if (contains(id)) copy(ids = ids - id) else copy(ids = ids + id)

    fun select(id: String): Selection = if (contains(id)) this else copy(ids = ids + id)

    fun deselect(id: String): Selection = if (contains(id)) copy(ids = ids - id) else this

    fun clear(): Selection = if (isEmpty) this else Selection()

    /**
     * Drops what is no longer there (a sync removed the row, a filter changed), keeping the pick order.
     */
    fun retain(available: Collection<String>): Selection {
        if (isEmpty) return this
        val kept = ids.filter { it in available }
        return if (kept.size == ids.size) this else Selection(kept)
    }

    /** Whether everything in view is picked: what "select all" would undo. */
    fun covers(visible: Collection<String>): Boolean = visible.isNotEmpty() && ids.containsAll(visible)

    /** Picks everything in view, after what was picked before it. */
    fun selectAll(visible: Collection<String>): Selection {
        val added = visible.filterNot { contains(it) }
        return if (added.isEmpty()) this else Selection(ids + added)
    }
}

/**
 * The mutable companion of [Selection]. A ViewModel keeps one and publishes the value every call
 * returns, so the screen still sees one immutable state.
 */
class SelectionController(initial: Selection = Selection()) {
    var state: Selection = initial
        private set

    val count: Int get() = state.count

    fun isSelected(id: String): Boolean = state.contains(id)

    fun toggle(id: String): Selection = state.toggle(id).also { state = it }

    fun select(id: String): Selection = state.select(id).also { state = it }

    fun deselect(id: String): Selection = state.deselect(id).also { state = it }

    fun clear(): Selection = state.clear().also { state = it }

    fun selectAll(visible: Collection<String>): Selection = state.selectAll(visible).also { state = it }

    fun retain(available: Collection<String>): Selection = state.retain(available).also { state = it }
}
