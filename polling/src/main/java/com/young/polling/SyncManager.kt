package com.young.polling

import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.OnLifecycleEvent
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor

/**
 * Created by young on 2018/7/2.
 */
object SyncManager {

    public const val POLLING_NEVER_STOP = -1


    interface TaskInfo<T> {
        fun getTaskId(): String
        /**
         * 最大轮询次数 无限(只通过[continuePolling]方法控制是否完成): [POLLING_NEVER_STOP]
         */
        fun getPollingCount(): Int

        fun getTimeInterval(): Long
        fun getPollingResultCallback(): PollingFutureTask.PollingResultCallback<T>
        fun getPollingCompleteCallback(): PollingFutureTask.PollingCompleteCallback<T>
        fun doInTask(): T
        fun getLifecycleOwner(): LifecycleOwner
        fun continuePolling(): Boolean
    }

    private val obs = object : LifecycleObserver {

        @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        fun onDestroy(lifecycleOwner: LifecycleOwner) {
//            println("Runnable ON_DESTROY -> $lifecycleOwner -> destroy")
            tasksByLifecycle[lifecycleOwner]?.forEach {
                it.remove(lifecycleOwner)
            }
            tasksByLifecycle.remove(lifecycleOwner)
//            println("Runnable LifecycleKeys -> ${tasksByLifecycle.keys}")
        }
    }

    private val doneCallback = object : PollingFutureTask.DoneCallback {
        override fun onDone(tId: String) {
//            println("Runnable doneCallback -> do onDone -> $tId")
            tasksById.remove(tId)
        }
    }


    private val threadPool: ThreadPoolExecutor = Executors.newCachedThreadPool() as ThreadPoolExecutor

    val tasksById = mutableMapOf<String, PollingFutureTask<*>>()
    val tasksByLifecycle = mutableMapOf<LifecycleOwner, MutableList<PollingFutureTask<*>>>()

    fun <T> sync(taskInfo: TaskInfo<T>, lifecycleOwner: LifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(obs)
        var futureTask: PollingFutureTask<T>? = tasksById[taskInfo.getTaskId()] as PollingFutureTask<T>?
        if (futureTask != null) {
            futureTask.addLifecycleOwner(lifecycleOwner)
            futureTask.addPollingResultCallback(lifecycleOwner, taskInfo.getPollingResultCallback())
            futureTask.addPollingCompleteCallback(lifecycleOwner, taskInfo.getPollingCompleteCallback())
        } else {
            futureTask = buildTask(taskInfo, lifecycleOwner)
            threadPool.submit(futureTask)
            tasksById.put(taskInfo.getTaskId(), futureTask)
        }
        var list = tasksByLifecycle[lifecycleOwner]
        if (list == null) {
            list = mutableListOf()
            tasksByLifecycle.put(lifecycleOwner, list)
        }
        list.add(futureTask)
    }

    private fun <T> buildTask(taskInfo: TaskInfo<T>, lifecycleOwner: LifecycleOwner): PollingFutureTask<T> {
        val t = object : AbstractSyncTask<T>(taskInfo.getTaskId(), taskInfo.getPollingCount(), taskInfo.getTimeInterval()) {
            override fun getTaskId(): String = taskInfo.getTaskId()

            override fun doTask(): T {
                return taskInfo.doInTask()
            }

            override fun continuePolling(): Boolean {
                return taskInfo.continuePolling()
            }
        }
        val futureTask = PollingFutureTask<T>(t, doneCallback, lifecycleOwner)
        t.pollingResultCallback = futureTask
        t.pollingCompleteCallback = futureTask
        futureTask.addPollingResultCallback(lifecycleOwner, taskInfo.getPollingResultCallback())
        futureTask.addPollingCompleteCallback(lifecycleOwner, taskInfo.getPollingCompleteCallback())
        return futureTask
    }

    fun cancel(task: IRealTask, lifecycleOwner: LifecycleOwner) {
        cancel(task.getTaskId(), lifecycleOwner)
    }

    fun cancel(taskId: String, lifecycleOwner: LifecycleOwner) {
        val task = tasksById[taskId]
        task?.apply {
            remove(lifecycleOwner)
        }
    }

    fun forceCancel(task: IRealTask) {
        forceCancel(task.getTaskId())
    }

    fun forceCancel(taskId: String) {
        val task = tasksById[taskId]
        task?.apply {
            this.forceCancel()
        }
    }

}