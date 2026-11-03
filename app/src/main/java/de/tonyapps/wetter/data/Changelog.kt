package de.tonyapps.wetter.data

import android.content.Context

data class Release(val title: String, val items: List<String>)

/** Der Update-Verlauf kommt aus changelog/CHANGELOG.md, derselben Datei wie auf GitHub. */
object Changelog {
    fun read(context: Context): List<Release> {
        val text = runCatching {
            context.assets.open("CHANGELOG.md").bufferedReader().use { it.readText() }
        }.getOrDefault("")
        val releases = mutableListOf<Release>()
        var title = ""
        var items = mutableListOf<String>()
        text.lineSequence().forEach { line ->
            when {
                line.startsWith("## ") -> {
                    if (title.isNotEmpty()) releases += Release(title, items)
                    title = line.removePrefix("## ").trim()
                    items = mutableListOf()
                }
                line.startsWith("- ") -> items += line.removePrefix("- ").trim()
            }
        }
        if (title.isNotEmpty()) releases += Release(title, items)
        return releases
    }

    private fun version(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    /** True beim ersten Start nach einem Update; merkt sich die Version sofort. */
    fun isNew(context: Context): Boolean {
        val prefs = context.getSharedPreferences("wetter", Context.MODE_PRIVATE)
        val current = version(context)
        val seen = prefs.getString("seen-version", null)
        prefs.edit().putString("seen-version", current).apply()
        return seen != current
    }
}
