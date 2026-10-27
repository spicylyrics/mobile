package com.tx24.spicyplayer.desktop.update

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** A `MAJOR.MINOR.PATCH` version, compared field by field as numbers (so 0.1.10 is after 0.1.9). */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion) = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
    override fun toString() = "$major.$minor.$patch"

    companion object {
        private val PLAIN = Regex("""(\d{1,9})\.(\d{1,9})\.(\d{1,9})""")
        private val TAG = Regex("""desktop-v(\d{1,9}\.\d{1,9}\.\d{1,9})""")

        /** "1.2.3", or null for anything else ("dev" included). */
        fun parse(text: String): AppVersion? = PLAIN.matchEntire(text.trim())?.destructured?.let { (a, b, c) ->
            AppVersion(a.toInt(), b.toInt(), c.toInt())
        }

        /** The version a desktop release tag names ("desktop-v1.2.3"), or null for any other tag. */
        fun fromTag(tag: String): AppVersion? = TAG.matchEntire(tag.trim())?.groupValues?.get(1)?.let(::parse)
    }
}

/** A desktop release newer than the running app. */
data class UpdateInfo(val version: String, val url: String, val published: String)

/**
 * The newest desktop release in a GitHub releases listing ([json], an array), or null when there is
 * none or the listing can't be read. Drafts and tags that aren't `desktop-vX.Y.Z` (the phone app's
 * `v*`) never count; prereleases count while their version is below 1.0.0, as every build is one until then.
 */
fun latestDesktopRelease(json: String): UpdateInfo? {
    val releases = runCatching { JsonParser.parseString(json) }.getOrNull()?.takeIf(JsonElement::isJsonArray)?.asJsonArray ?: return null
    return releases.asSequence()
        .filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject)
        .filterNot { it.bool("draft") }
        .mapNotNull { release -> AppVersion.fromTag(release.text("tag_name"))?.let { it to release } }
        .filter { (version, release) -> !release.bool("prerelease") || version < AppVersion(1, 0, 0) }
        .maxByOrNull { it.first }
        ?.let { (version, release) -> UpdateInfo(version.toString(), release.text("html_url"), release.text("published_at")) }
}

/** [latest] when it is newer than [running]; a running version that isn't X.Y.Z ("dev") never has an update. */
fun updateFor(running: String, latest: UpdateInfo?): UpdateInfo? {
    val current = AppVersion.parse(running) ?: return null
    val newest = latest?.let { AppVersion.parse(it.version) } ?: return null
    return latest.takeIf { newest > current }
}

private fun JsonObject.text(name: String) = get(name)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

private fun JsonObject.bool(name: String) = get(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asBoolean }.getOrNull() } == true
