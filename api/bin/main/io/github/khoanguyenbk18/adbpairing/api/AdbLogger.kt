package io.github.khoanguyenbk18.adbpairing.api

/**
 * Functional interface for plugging in application-specific loggers.
 */
fun interface AdbLogger {
    fun log(priority: Int, tag: String, message: String, throwable: Throwable?)

    companion object {
        const val VERBOSE = 2
        const val DEBUG = 3
        const val INFO = 4
        const val WARN = 5
        const val ERROR = 6
    }
}

fun AdbLogger.log(priority: Int, tag: String, message: String) {
    log(priority, tag, message, null)
}

object NoOpAdbLogger : AdbLogger {
    override fun log(priority: Int, tag: String, message: String, throwable: Throwable?) {}
}
