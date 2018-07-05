package com.young.polling

import android.os.Handler
import android.os.Looper

/**
 * Created by young on 2018/7/2.
 */
abstract class AbstractSyncTask<T>(
        private val tId: String,
        private val pollingCount: Int,
        private val timeInterval: Long,
        var pollingResultCallback: PollingResultCallback<T>? = null,
        var pollingCompleteCallback: PollingCompleteCallback<T>? = null
) : IRealTask {

    interface PollingResultCallback<in T> {
        fun onPollingResult(tId: String, result: T?)
    }

    interface PollingCompleteCallback<in T> {
        fun onCompleted(tId: String, result: T?)
    }

    private var count = 0
    private var lastPollingResult: T? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun run() {
        while (continuePolling(tId) && (pollingCount == SyncManager.POLLING_NEVER_STOP || count <= pollingCount)) {
            lastPollingResult = doTask()
            pollingResultCallback?.apply {
                mainHandler.post { onPollingResult(tId, lastPollingResult) }
            }
            count++
            if (!continuePolling(tId) || (pollingCount != SyncManager.POLLING_NEVER_STOP && count > pollingCount)) {
                break
            }
            Thread.sleep(timeInterval)
        }
        pollingCompleteCallback?.apply {
            mainHandler.post { onCompleted(tId, lastPollingResult) }
        }
    }

    abstract fun doTask(): T

    abstract fun continuePolling(tId: String): Boolean

}