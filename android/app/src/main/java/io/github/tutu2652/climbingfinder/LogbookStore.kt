package io.github.tutu2652.climbingfinder

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.tutu2652.climbingfinder.core.ClimbSession
import io.github.tutu2652.climbingfinder.core.ColourGrade
import io.github.tutu2652.climbingfinder.core.LogbookBackup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import java.io.File

/**
 * Your climbing log, kept in one JSON file in the app's private storage, in the
 * same format as a backup. Every change is written straight away, in order.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LogbookStore(private val file: File, private val scope: CoroutineScope) {
    private val stored: LogbookBackup? = runCatching { LogbookBackup.decode(file.readText()) }.getOrNull()

    /** Newest first. */
    var sessions: List<ClimbSession> by mutableStateOf(stored?.sessions.orEmpty().sortedByDescending { it.start }); private set

    /**
     * Colour grades you've set up yourself, by gym id. They replace the gym's chart from
     * gyms.json; an empty list means you've removed the gym's colours.
     */
    var colourScales: Map<String, List<ColourGrade>> by mutableStateOf(stored?.colourScales.orEmpty()); private set

    private val writer = Dispatchers.IO.limitedParallelism(1)

    /** Your colours for a gym, or null to use the gym's own chart. */
    fun setColourScale(gymID: String, scale: List<ColourGrade>?) {
        colourScales = if (scale == null) colourScales - gymID else colourScales + (gymID to scale)
        save(sessions)
    }

    fun session(id: String): ClimbSession? = sessions.firstOrNull { it.id == id }
    val active: ClimbSession? get() = sessions.firstOrNull { it.isActive }

    fun insert(s: ClimbSession) = save(sessions + s)

    fun update(id: String, change: (ClimbSession) -> ClimbSession) =
        save(sessions.map { if (it.id == id) change(it) else it })

    fun delete(id: String) = save(sessions.filterNot { it.id == id })

    /** Adds sessions from a backup that aren't here yet; returns how many. */
    fun restore(backup: LogbookBackup): Int {
        val added = backup.newSessions(sessions)
        val scales = backup.mergedColourScales(colourScales)
        if (added.isNotEmpty() || scales != colourScales) {
            colourScales = scales
            save(sessions + added)
        }
        return added.size
    }

    fun backupText(): String =
        LogbookBackup(sessions = sessions.sortedBy { it.start }, colourScales = colourScales.ifEmpty { null }).encode()

    private fun save(list: List<ClimbSession>) {
        sessions = list.sortedByDescending { it.start }
        val text = backupText()
        scope.launch(writer) {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }
    }
}
