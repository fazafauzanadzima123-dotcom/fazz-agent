package com.orbit.agent.core.tools.impl

import java.text.Normalizer

data class InstalledApp(val label: String, val packageName: String)

/** Pure, testable matching of a spoken/typed app name to an installed app. */
object AppMatcher {

    fun normalize(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    /** Returns the best match or null when nothing is a confident match. */
    fun bestMatch(query: String, apps: List<InstalledApp>): InstalledApp? {
        val q = normalize(query).removeSuffix(" app").trim()
        if (q.isEmpty()) return null
        return apps.map { it to score(q, it) }
            .filter { it.second > 0 }
            .maxWithOrNull(compareBy<Pair<InstalledApp, Int>> { it.second }.thenBy { -it.first.label.length })
            ?.first
    }

    fun suggestions(query: String, apps: List<InstalledApp>, limit: Int = 5): List<InstalledApp> {
        val tokens = normalize(query).split(" ").filter { it.length >= 2 }
        return apps.filter { app ->
            val l = normalize(app.label)
            tokens.any { l.contains(it) || app.packageName.lowercase().contains(it) }
        }.take(limit)
    }

    private fun score(q: String, app: InstalledApp): Int {
        val label = normalize(app.label)
        val compactQ = q.replace(" ", "")
        val compactL = label.replace(" ", "")
        return when {
            label == q -> 100
            compactL == compactQ -> 95
            label.startsWith("$q ") || label.startsWith(q) && q.length >= 3 -> 80
            label.split(" ").contains(q) -> 70
            q.length >= 4 && label.contains(q) -> 60
            q.length >= 4 && app.packageName.lowercase().split('.').any { it == compactQ } -> 50
            else -> 0
        }
    }
}
