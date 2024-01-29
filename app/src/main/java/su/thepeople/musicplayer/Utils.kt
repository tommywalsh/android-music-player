package su.thepeople.musicplayer

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

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
