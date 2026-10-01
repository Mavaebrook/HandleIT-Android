package com.ai.assistance.operit.api.chat.library

import android.content.Context
import com.ai.assistance.operit.data.preferences.preferencesManager
import com.ai.assistance.operit.data.repository.MemoryRepository
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 记忆衰减轮询器 (Memory Decay Scheduler)
 *
 * 镜像 [MemoryAutoSaveScheduler] 的周期扫描模式：
 *  - 单例 + `start()` 启动后台循环（`LOOP_TICK_MS=60s` 心跳）；
 *  - `runOnce()` 用 `AtomicBoolean` 防重入；
 *  - 每个记忆空间独立调度（`nextRunAtMsByProfileId`），默认每天执行一次衰减扫描。
 *
 * 衰减逻辑本身位于 [MemoryRepository.runDecaySweep]，本类只负责调度。
 */
class MemoryDecayScheduler(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "MemoryDecayScheduler"
        private const val LOOP_TICK_MS = 60 * 1000L

        /** 衰减扫描默认间隔：每天一次。 */
        const val DECAY_INTERVAL_MS = 24L * 60L * 60L * 1000L

        @Volatile
        private var instance: MemoryDecayScheduler? = null

        fun getInstance(): MemoryDecayScheduler? = instance
    }

    private val isRunning = AtomicBoolean(false)
    @Volatile
    private var loopJob: Job? = null
    private val nextRunAtMsByProfileId = ConcurrentHashMap<String, Long>()

    fun start() {
        if (loopJob?.isActive == true) return
        instance = this
        loopJob =
            scope.launch(Dispatchers.IO) {
                AppLogger.d(TAG, "记忆衰减轮询器已启动")
                while (isActive) {
                    delay(LOOP_TICK_MS)
                    runOnce()
                }
            }
    }

    suspend fun runOnce() {
        if (!isRunning.compareAndSet(false, true)) {
            AppLogger.d(TAG, "上一轮记忆衰减扫描仍在运行，跳过本轮")
            return
        }
        try {
            scanAndDecay()
        } finally {
            isRunning.set(false)
        }
    }

    private suspend fun scanAndDecay() {
        val profileIds = preferencesManager.memorySpaceListFlow.first()
        if (profileIds.isEmpty()) return

        val nowMs = System.currentTimeMillis()
        for (profileId in profileIds) {
            val nextRunAtMs = getOrInitNextRunAtMs(profileId, nowMs)
            if (nowMs < nextRunAtMs) {
                continue
            }

            val repository = MemoryRepository(context, profileId)
            val result = repository.runDecaySweep(nowMs)
            AppLogger.d(
                TAG,
                "记忆衰减扫描完成: profileId=$profileId, " +
                    "examined=${result.examined}, decayed=${result.decayed}, " +
                    "inactivated=${result.inactivated}"
            )
            scheduleNextRun(profileId, nowMs + DECAY_INTERVAL_MS)
        }
    }

    private fun getOrInitNextRunAtMs(profileId: String, nowMs: Long): Long {
        nextRunAtMsByProfileId[profileId]?.takeIf { it > 0L }?.let { return it }
        val target = nowMs + DECAY_INTERVAL_MS
        scheduleNextRun(profileId, target)
        return target
    }

    private fun scheduleNextRun(profileId: String, nextRunAtMs: Long) {
        nextRunAtMsByProfileId[profileId] = nextRunAtMs.coerceAtLeast(0L)
    }
}
