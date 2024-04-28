package su.thepeople.musicplayer.data

/**
 * Our database uses integer IDs (aka "internal IDs") that are unique on a per-table basis only.  The Android media APIs assume that every item has
 * an ID (aka "external ID") that is a globally unique string.  The utilities in this file help with generating these IDs and converting between them.
 */

/**
 * Each type gets its own string prefix on its external IDs.
 */
const val BAND_PREFIX = "band:"
const val ALBUM_PREFIX = "album:"
const val SONG_PREFIX = "song:"
const val DECADE_PREFIX = "decade:"
const val GROUP_PREFIX = "group:"
const val YEAR_PREFIX = "year:"

/**
 * These extension functions convert an internal integer ID to an external string ID
 */
fun Band.externalId(): String {
    return BAND_PREFIX + id.toString()
}

fun Album.externalId(): String {
    return ALBUM_PREFIX + id.toString()
}

fun Song.externalId(): String {
    return SONG_PREFIX + id.toString()
}

/**
 * Converts an external string ID (for any type) into an internal integer ID
 */
fun internalIntId(externalId: String): Int {
    return externalId.substringAfter(":").toInt()
}

fun internalStringId(externalId: String): String {
    return externalId.substringAfter(":")
}
