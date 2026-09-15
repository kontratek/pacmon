package dev.pacmon.jetbrains.service

import com.intellij.util.messages.Topic

fun interface PacmonNotesListener {
    fun notesChanged(paths: Set<String>)

    companion object {
        @JvmField
        val TOPIC: Topic<PacmonNotesListener> = Topic.create("Pacmon notes changed", PacmonNotesListener::class.java)
    }
}
