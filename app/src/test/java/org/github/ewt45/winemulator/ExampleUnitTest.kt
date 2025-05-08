package org.github.ewt45.winemulator

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import org.junit.Test

import org.junit.Assert.*
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }


    class RateLimiter {
        private val lastBlock = AtomicReference<(suspend () -> Unit)?>(null)
        private val scope = CoroutineScope(Dispatchers.Default)

        fun runDelay(key:Int, block: suspend () -> Unit) {
            lastBlock.set(block)
            scope.launch {
                delay(1000)
                if (lastBlock.get() == block) //最后一次设置之后，过了一秒没改过
                    block()
            }

//            scope.launch {
//                println("$key.1. CoroutineScope(Dispatchers.Default).launch")
//                mutex.withLock {
//                    println("$key.2. mutex.withLock")
//                    currentJob.getAndSet(null)?.cancel() // Cancel previous job if any
//                    val newJob = launch {
//                        println("$key.3. newJob = launch")
//                        delay(1.seconds)
//                        println("$key.4. newJob = launch after delay")
//                        block() // Execute the code block after 1 second
//                    }
//                    newJob.invokeOnCompletion { th ->
//                        println(if (th==null) "job$key.执行完成" else "$key.被取消")
//                    }
//                    currentJob.set(newJob)
//                }
//            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default)

    @Test
    fun fun1() {
        RateLimiter().apply {
            runDelay(1) { println("输出 1") }
            runDelay(2) { println("输出 2") }
            runDelay(3) { println("输出 3") }
            runDelay(4) { println("输出 4") }
            runDelay(5) { println("输出 5") }
        }
        println("输出？？？？？？？")
        val curr = System.currentTimeMillis()
        while (System.currentTimeMillis() - curr < 2000) {
            continue
        }

    }



}