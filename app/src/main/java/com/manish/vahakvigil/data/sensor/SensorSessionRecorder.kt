package com.manish.vahakvigil.data.sensor

import com.manish.vahakvigil.domain.model.RawSensorEvent
import com.manish.vahakvigil.domain.model.RideSessionStatus
import com.manish.vahakvigil.domain.repository.RideSessionRepository
import com.manish.vahakvigil.domain.repository.SensorEventRepository
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class SensorRecordingStatus { IDLE, STARTING, RECORDING, UNAVAILABLE, STOPPING, ERROR }

data class SensorRecordingDiagnostics(
    val status: SensorRecordingStatus = SensorRecordingStatus.IDLE,
    val activeSessionId: String? = null,
    val receivedEventCount: Long = 0,
    val persistedEventCount: Long = 0,
    val pendingEventCount: Int = 0,
    val sourceDroppedEventCount: Long = 0,
    val recorderDroppedEventCount: Long = 0,
    val failedBatchCount: Long = 0,
    val failedEventCount: Long = 0,
    val lastError: String? = null
)

/** Collects one ride's raw sensor stream and persists bounded batches outside sensor callbacks. */
class SensorSessionRecorder(
    private val source: RawSensorEventSource,
    private val sensorEventRepository: SensorEventRepository,
    private val rideSessionRepository: RideSessionRepository,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val queueCapacity: Int = DEFAULT_QUEUE_CAPACITY,
    private val retryDelayMillis: Long = DEFAULT_RETRY_DELAY_MILLIS,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val lifecycleMutex = Mutex()
    private val pendingCount = AtomicInteger(0)
    private val receivedCount = AtomicLong(0)
    private val _diagnostics = MutableStateFlow(SensorRecordingDiagnostics())
    val diagnostics: StateFlow<SensorRecordingDiagnostics> = _diagnostics.asStateFlow()

    private var activeSessionId: String? = null
    private var eventQueue: Channel<RawSensorEvent>? = null
    private var collectorJob: Job? = null
    private var writerJob: Job? = null

    init {
        require(batchSize > 0) { "batchSize must be positive" }
        require(queueCapacity > 0) { "queueCapacity must be positive" }
        require(retryDelayMillis >= 0L) { "retryDelayMillis must not be negative" }
    }

    /** Starts once for a verified active session. Repeated starts for that session are idempotent. */
    suspend fun start(sessionId: String): Boolean = lifecycleMutex.withLock {
        if (activeSessionId == sessionId && collectorJob?.isActive == true) return@withLock true
        if (activeSessionId != null || collectorJob?.isActive == true) {
            setError("A different ride session is already recording")
            return@withLock false
        }
        if (sessionId.isBlank()) {
            val discarded = source.drainPendingEvents().size
            _diagnostics.update {
                it.copy(
                    status = SensorRecordingStatus.UNAVAILABLE,
                    activeSessionId = null,
                    recorderDroppedEventCount = it.recorderDroppedEventCount + discarded,
                    sourceDroppedEventCount = source.droppedEventCount,
                    lastError = "Ride session ID is unavailable"
                )
            }
            return@withLock false
        }

        _diagnostics.value = SensorRecordingDiagnostics(
            status = SensorRecordingStatus.STARTING,
            activeSessionId = sessionId,
            sourceDroppedEventCount = source.droppedEventCount
        )
        receivedCount.set(0)
        pendingCount.set(0)

        val session = try {
            withContext(ioDispatcher) { rideSessionRepository.getSession(sessionId) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            reportUnavailable("Could not verify ride session: ${error.messageOrType()}")
            return@withLock false
        }
        if (session == null || session.status != RideSessionStatus.ACTIVE) {
            val discarded = source.drainPendingEvents().size
            _diagnostics.update { current -> current.copy(
                status = SensorRecordingStatus.UNAVAILABLE,
                activeSessionId = null,
                recorderDroppedEventCount = current.recorderDroppedEventCount + discarded,
                sourceDroppedEventCount = source.droppedEventCount,
                lastError = "Ride session is missing or is not active"
            ) }
            return@withLock false
        }

        val queue = Channel<RawSensorEvent>(capacity = queueCapacity)
        activeSessionId = sessionId
        eventQueue = queue
        writerJob = scope.launch {
            writeBatches(sessionId, queue)
        }

        // The source already has a bounded pre-collector queue. Preserve its older events first.
        source.drainPendingEvents().forEach(::enqueue)
        _diagnostics.update { it.copy(
            status = SensorRecordingStatus.RECORDING,
            activeSessionId = sessionId,
            sourceDroppedEventCount = source.droppedEventCount
        ) }
        collectorJob = scope.launch {
            try {
                source.rawSensorEvents.collect(::enqueue)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _diagnostics.update { it.copy(
                    status = SensorRecordingStatus.ERROR,
                    lastError = "Sensor event collection failed: ${error.messageOrType()}"
                ) }
            }
        }
        true
    }

    /** Reports that session creation failed; safety monitoring remains independent. */
    fun reportUnavailable(reason: String) {
        _diagnostics.update { current ->
            if (current.status == SensorRecordingStatus.RECORDING && current.activeSessionId != null) {
                current
            } else {
                current.copy(
                    status = SensorRecordingStatus.UNAVAILABLE,
                    activeSessionId = null,
                    sourceDroppedEventCount = source.droppedEventCount,
                    lastError = reason
                )
            }
        }
    }

    /** Stops collection, drains the source's bounded queue, then flushes all accepted batches. */
    suspend fun stopAndFlush() = lifecycleMutex.withLock {
        val sessionId = activeSessionId
        val queue = eventQueue
        _diagnostics.update { it.copy(status = SensorRecordingStatus.STOPPING) }

        collectorJob?.cancelAndJoin()
        collectorJob = null

        val finalSourceEvents = source.drainPendingEvents()
        if (sessionId != null && queue != null) {
            finalSourceEvents.forEach(::enqueue)
            queue.close()
            writerJob?.join()
        } else {
            if (finalSourceEvents.isNotEmpty()) {
                _diagnostics.update {
                    it.copy(recorderDroppedEventCount = it.recorderDroppedEventCount + finalSourceEvents.size)
                }
            }
        }

        activeSessionId = null
        eventQueue = null
        writerJob = null
        pendingCount.set(0)
        _diagnostics.update { current -> current.copy(
            status = if (current.status == SensorRecordingStatus.ERROR) {
                SensorRecordingStatus.ERROR
            } else {
                SensorRecordingStatus.IDLE
            },
            activeSessionId = null,
            receivedEventCount = receivedCount.get(),
            pendingEventCount = 0,
            sourceDroppedEventCount = source.droppedEventCount
        ) }
    }

    private fun enqueue(event: RawSensorEvent) {
        val queue = eventQueue ?: run {
            _diagnostics.update {
                it.copy(
                    receivedEventCount = receivedCount.get(),
                    recorderDroppedEventCount = it.recorderDroppedEventCount + 1,
                    sourceDroppedEventCount = source.droppedEventCount
                )
            }
            return
        }
        receivedCount.incrementAndGet()
        pendingCount.incrementAndGet()
        if (queue.trySend(event).isFailure) {
            val pending = pendingCount.decrementAndGet().coerceAtLeast(0)
            _diagnostics.update {
                it.copy(
                    receivedEventCount = receivedCount.get(),
                    pendingEventCount = pending,
                    recorderDroppedEventCount = it.recorderDroppedEventCount + 1,
                    sourceDroppedEventCount = source.droppedEventCount
                )
            }
        }
    }

    private suspend fun writeBatches(sessionId: String, queue: Channel<RawSensorEvent>) {
        val batch = ArrayList<RawSensorEvent>(batchSize)
        try {
            for (event in queue) {
                batch += event
                if (batch.size >= batchSize) {
                    persistBatch(sessionId, batch)
                    batch.clear()
                }
            }
            if (batch.isNotEmpty()) persistBatch(sessionId, batch)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            _diagnostics.update { it.copy(
                receivedEventCount = receivedCount.get(),
                status = SensorRecordingStatus.ERROR,
                failedBatchCount = it.failedBatchCount + if (batch.isEmpty()) 0 else 1,
                failedEventCount = it.failedEventCount + batch.size,
                lastError = "Sensor event writer failed: ${error.messageOrType()}"
            ) }
            pendingCount.addAndGet(-batch.size)
            _diagnostics.update {
                it.copy(
                    receivedEventCount = receivedCount.get(),
                    pendingEventCount = pendingCount.get().coerceAtLeast(0)
                )
            }
        }
    }

    private suspend fun persistBatch(sessionId: String, batch: List<RawSensorEvent>) {
        var lastError: Exception? = null
        repeat(MAX_INSERT_ATTEMPTS) { attempt ->
            try {
                withContext(ioDispatcher) {
                    sensorEventRepository.insertBatch(sessionId, batch.toList())
                }
                pendingCount.addAndGet(-batch.size)
                _diagnostics.update {
                    it.copy(
                        receivedEventCount = receivedCount.get(),
                        persistedEventCount = it.persistedEventCount + batch.size,
                        pendingEventCount = pendingCount.get().coerceAtLeast(0),
                        sourceDroppedEventCount = source.droppedEventCount,
                        lastError = null
                    )
                }
                return
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                lastError = error
                _diagnostics.update { it.copy(
                    lastError = "Sensor event batch insert failed: ${error.messageOrType()}"
                ) }
                if (attempt < MAX_INSERT_ATTEMPTS - 1 && retryDelayMillis > 0L) delay(retryDelayMillis)
            }
        }

        pendingCount.addAndGet(-batch.size)
        _diagnostics.update {
            it.copy(
                receivedEventCount = receivedCount.get(),
                status = SensorRecordingStatus.ERROR,
                failedBatchCount = it.failedBatchCount + 1,
                failedEventCount = it.failedEventCount + batch.size,
                pendingEventCount = pendingCount.get().coerceAtLeast(0),
                sourceDroppedEventCount = source.droppedEventCount,
                lastError = "Sensor event batch abandoned after bounded retries: ${lastError?.messageOrType()}"
            )
        }
    }

    private fun setError(message: String) {
        _diagnostics.update { it.copy(
            status = SensorRecordingStatus.ERROR,
            lastError = message
        ) }
    }

    private fun Throwable.messageOrType(): String = message?.takeIf(String::isNotBlank) ?: this::class.java.simpleName

    companion object {
        const val DEFAULT_BATCH_SIZE = 64
        const val DEFAULT_QUEUE_CAPACITY = 512
        const val DEFAULT_RETRY_DELAY_MILLIS = 250L
        private const val MAX_INSERT_ATTEMPTS = 2
    }
}
