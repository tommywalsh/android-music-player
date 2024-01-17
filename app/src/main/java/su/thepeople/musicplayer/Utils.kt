package su.thepeople.musicplayer

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

// TODO: eliminate the duplicate of this object, which is over on the UI side somewhere.
fun <T> successCallback(task: (T)->Unit): FutureCallback<T> {
    return object: FutureCallback<T> {
        override fun onSuccess(result: T) {
            task(result)
        }
        override fun onFailure(t: Throwable) {
            // TODO: what happens here???
        }
    }
}

fun <T> ListenableFuture<T>.onSuccess(context: Context, callback: (T) -> Unit) {
    Futures.addCallback(this, object: FutureCallback<T> {
        override fun onSuccess(value: T) {
            Log.d("LibraryUI", "library success value of ${value?.toString()}")
            callback(value)
        }
        override fun onFailure(t: Throwable) {
            // TODO: what to do about errors?
        }
    }, ContextCompat.getMainExecutor(context))
}


