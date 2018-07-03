package com.young.polling

import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.OnLifecycleEvent
import java.util.concurrent.FutureTask

/**
 * Created by young on 2018/7/2.
 */
class PollingFutureTask<T>(private val task: IRealTask,
                           private val doneCallback: DoneCallback?,
                           lifecycleOwner: LifecycleOwner) : FutureTask<Unit>(task, Unit), AbstractSyncTask.PollingResultCallback<T>, AbstractSyncTask.PollingCompleteCallback<T> {

    interface DoneCallback {
        fun onDone(tId: String)
    }

    interface PollingResultCallback<in T> {
        fun onPollingResult(tId: String, result: T?)
    }

    interface PollingCompleteCallback<in T> {
        fun onCompleted(tId: String, lastPollingResult: T?)
    }

    private val lifecycleOwners = mutableSetOf<LifecycleOwner>()
    private val callbacks = hashMapOf<LifecycleOwner, MutableSet<PollingResultCallback<T>>>()
    private val completeCallbacks = hashMapOf<LifecycleOwner, MutableSet<PollingCompleteCallback<T>>>()
    private val obs = object : LifecycleObserver {
        @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        fun onDestroy(lifecycleOwner: LifecycleOwner) {
            clearCallbacks(lifecycleOwner)
        }
    }

    init {
        addLifecycleOwner(lifecycleOwner)
    }

    fun addPollingResultCallback(lifecycleOwner: LifecycleOwner, pollingResultCallback: PollingResultCallback<T>?) {
        if (pollingResultCallback != null) {
            var list = callbacks[lifecycleOwner]
            if (list==null){
                list = mutableSetOf()
                callbacks.put(lifecycleOwner, list)
            }
            list.add(pollingResultCallback)
        }
    }

    fun addPollingCompleteCallback(lifecycleOwner: LifecycleOwner, pollingCompleteCallback: PollingCompleteCallback<T>?) {
        if (pollingCompleteCallback != null) {
            var list = completeCallbacks[lifecycleOwner]
            if (list==null){
                list = mutableSetOf()
                completeCallbacks.put(lifecycleOwner, list)
            }
            list.add(pollingCompleteCallback)
        }
    }

    fun addLifecycleOwner(lifecycleOwner: LifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(obs)
        lifecycleOwners.add(lifecycleOwner)
    }

    fun remove(lifecycleOwner: LifecycleOwner) {
//        println("Runnable TaskCounter remove -> $lifecycleOwner")
        lifecycleOwners.remove(lifecycleOwner)
        if (lifecycleOwners.isEmpty()) {
            doCancel()
        }
    }

    fun forceCancel() {
//        println("Runnable TaskCounter clear -> ForceCancel()")
        lifecycleOwners.clear()
        doCancel()
    }

    override fun onCompleted(tId: String, result: T?) {
        for ((_, v) in completeCallbacks) {
            v.forEach {
                it.onCompleted(tId, result)
            }
        }
    }

    override fun onPollingResult(tId: String, result: T?) {
        for ((_, v) in callbacks) {
            v.forEach {
                it.onPollingResult(tId, result)
            }
        }
    }

    private fun doCancel() {
//        println("Runnable TaskCounter lifecycleOwners isEmpty -> do cancel")
        cancel(true)
    }

    override fun done() {
        super.done()
//        println("Thread has done")
        doneCallback?.onDone(task.getTaskId())
    }

    private fun clearCallbacks(lifecycleOwner: LifecycleOwner){
        callbacks.remove(lifecycleOwner)
        completeCallbacks.remove(lifecycleOwner)
    }
}