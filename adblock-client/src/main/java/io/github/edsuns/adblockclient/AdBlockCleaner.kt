package io.github.edsuns.adblockclient

import java.lang.ref.PhantomReference
import java.lang.ref.ReferenceQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 通用 Native 内存守护回收机制 (兼容 Android API 26+)
 *
 * 通过标准 PhantomReference 与守护线程构建轻量级 Cleaner：
 * 1. 显式调用 close() 时立即清理并解绑；
 * 2. 若外部发生异常或丢失引用被 GC，由后台守护线程在检测到 PhantomReachability 时自动触发释放；
 * 3. 严格保证无论并发还是重复触发，析构动作至多执行一次 (At most once / Idempotent)。
 */
internal object AdBlockCleaner {
    private val queue = ReferenceQueue<Any>()
    private val activeRefs = ConcurrentHashMap.newKeySet<NativeCleanableRef>()

    init {
        val daemon =
            Thread(
                {
                    while (true) {
                        try {
                            val ref = queue.remove() as? NativeCleanableRef ?: continue
                            activeRefs.remove(ref)
                            ref.clean()
                        } catch (e: InterruptedException) {
                            break
                        } catch (_: Throwable) {
                            // 忽略释放过程中的非致命异常，保持守护线程常驻
                        }
                    }
                },
                "AdBlockNativeCleanerDaemon",
            )
        daemon.isDaemon = true
        daemon.start()
    }

    fun register(
        referent: Any,
        cleanupAction: () -> Unit,
    ): Cleanable {
        val ref = NativeCleanableRef(referent, queue, cleanupAction)
        activeRefs.add(ref)
        return ref
    }

    interface Cleanable {
        fun clean()
    }

    private class NativeCleanableRef(
        referent: Any,
        queue: ReferenceQueue<Any>,
        private val cleanupAction: () -> Unit,
    ) : PhantomReference<Any>(referent, queue),
        Cleanable {
        private val isCleaned = AtomicBoolean(false)

        override fun clean() {
            if (isCleaned.compareAndSet(false, true)) {
                try {
                    cleanupAction()
                } finally {
                    clear()
                    activeRefs.remove(this)
                }
            }
        }
    }
}
