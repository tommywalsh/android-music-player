package su.thepeople.musicplayer.tools

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executor
import java.util.concurrent.Executors

// This class does not currently support return values being passed to callbacks, but that could be easily added
class BackgroundRunner(looper: Looper, private val executor: Executor, backgroundTask: ()->Unit, foregroundCallback: ()->Unit) {
    private val handler = Handler(looper)

    private val backgroundTaskWrapper = Runnable {
        backgroundTask()
        handler.post {
            foregroundCallback()
        }
    }

    fun start() {
        executor.execute(backgroundTaskWrapper)
    }
}

fun runInBackground(context: Context, backgroundTask: ()->Unit, foregroundCallback: ()->Unit) {
    val executor = Executors.newSingleThreadExecutor()
    val runner = BackgroundRunner(context.mainLooper, executor, backgroundTask, foregroundCallback)
    runner.start()
}
