package su.thepeople.musicplayer

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

fun <T> successCallback(task: (T)->Unit): FutureCallback<T> {
    return object: FutureCallback<T> {
        override fun onSuccess(result: T) {
            task(result)
        }
        override fun onFailure(t: Throwable) {
            Log.d("Communication", "Error received in future", t)
        }
    }
}

fun <T> ListenableFuture<T>.onSuccess(context: Context, callback: (T) -> Unit) {
    Futures.addCallback(this, successCallback(callback), ContextCompat.getMainExecutor(context))
}

fun jsonArrayToStringList(arr: JSONArray): List<String> {
    return (0 until arr.length()).asSequence().map{arr.getString(it)}.toList()
}

fun getStringArrayProp(obj: JSONObject, propName: String): List<String> {
    if (obj.has(propName)) {
        try {
            val rawProp = obj.get(propName)
            if (rawProp is JSONArray) {
                return jsonArrayToStringList(rawProp)
            } else if (rawProp is String) {
                return listOf(rawProp)
            }
        } catch (ex: JSONException) {
            // Just fall through and return nothing
        }
    }
    return listOf()
}

fun songMediaItemToJSON(mi: MediaItem): JSONObject {
    val json = JSONObject()
    val metadata = mi.mediaMetadata

    json.put("artistName", metadata.artist.toString())
    json.put("songTitle", metadata.title.toString())
    json.put("songDisplayTitle", metadata.displayTitle.toString())
    json.put("trackNumber", metadata.trackNumber?:0)
    json.put("releaseYear", metadata.releaseYear?:0)
    json.put("albumTitle", metadata.albumTitle)
    json.put("mediaId", mi.mediaId)

    val dbIdBundle = metadata.extras!!
    json.put("bandId", dbIdBundle.getInt("band"))
    json.put("songId", dbIdBundle.getInt("song"))
    val albumId = dbIdBundle.getInt("album", -1)
    if (albumId != -1) {
        json.put("albumId", albumId)
    }

    json.put("uri", mi.localConfiguration!!.uri.toString())

    return json
}

fun songMediaItemFromJSON(json: JSONObject): MediaItem {

    val builder = MediaMetadata.Builder()
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .setArtist(json.getString("artistName"))
        .setTitle(json.getString("songTitle"))
        .setDisplayTitle(json.getString("songDisplayTitle"))
        .setTrackNumber(json.getInt("trackNumber"))
        .setReleaseYear(json.getInt("releaseYear"))

    if (json.has("albumTitle")) {
        builder.setAlbumTitle(json.getString("albumTitle"))
    }

    val extras = Bundle()
    extras.putInt("band", json.getInt("bandId"))
    extras.putInt("song", json.getInt("songId"))
    if (json.has("albumId")) {
        extras.putInt("album", json.getInt("albumId"))
    }
    builder.setExtras(extras)

    return MediaItem.Builder()
        .setMediaId(json.getString("mediaId"))
        .setMediaMetadata(builder.build())
        .setUri(json.getString("uri"))
        .build()
}
