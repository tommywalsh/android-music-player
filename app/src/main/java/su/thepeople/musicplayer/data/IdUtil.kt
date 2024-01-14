package su.thepeople.musicplayer.data

const val BAND_PREFIX = "band:"
const val ALBUM_PREFIX = "album:"
const val SONG_PREFIX = "song:"

fun Band.externalId(): String {
    return BAND_PREFIX + id.toString()
}

fun Album.externalId(): String {
    return ALBUM_PREFIX + id.toString()
}

fun Song.externalId(): String {
    return SONG_PREFIX + id.toString()
}

fun dbId(externalId: String): Int {
    return externalId.substringAfter(":").toInt()
}
