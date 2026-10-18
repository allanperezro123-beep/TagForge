package com.tagforge.app.infrastructure.tasks

import com.tagforge.app.infrastructure.tasks.handlers.HttpRequestHandler
import com.tagforge.domain.task.ActionOutcome
import com.tagforge.domain.task.FailReason
import com.tagforge.domain.task.HttpMethod
import com.tagforge.domain.task.TaskAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** W9: the HTTP task is over after at most 10 s whatever the server does, including one that answers a byte at a time. */
class HttpRequestHandlerTest {

    /** A server that sends [response] one byte every [gapMillis] after reading the request. */
    private fun dribblingServer(response: String, gapMillis: Long): ServerSocket {
        val server = ServerSocket(0)
        Thread {
            runCatching {
                server.accept().use { socket ->
                    val input = socket.getInputStream()
                    val seen = StringBuilder()
                    while (!seen.endsWith("\r\n\r\n")) seen.append(input.read().toChar())
                    for (byte in response.toByteArray()) {
                        socket.getOutputStream().write(byte.toInt())
                        socket.getOutputStream().flush()
                        Thread.sleep(gapMillis)
                    }
                }
            }
        }.apply { isDaemon = true }.start()
        return server
    }

    private fun get(server: ServerSocket) = TaskAction.HttpRequest("http://127.0.0.1:${server.localPort}/", HttpMethod.GET)

    private fun seconds(since: Long) = (System.nanoTime() - since) / 1_000_000_000.0

    @Test
    fun theDefaultDeadlineIsTenSeconds() {
        assertEquals(10_000L, HttpRequestHandler.DEADLINE_MILLIS)
    }

    @Test
    fun aServerThatAnswersOneByteAtATimeIsCutOffAtTheOverallDeadline() = runBlocking {
        // 28 bytes, one every 200 ms: every read is far inside the 5 s read timeout, the whole answer takes 5.6 s.
        val server = dribblingServer("HTTP/1.1 204 No Content\r\n\r\n", gapMillis = 200)
        val started = System.nanoTime()

        val outcome = HttpRequestHandler(Dispatchers.IO, deadlineMillis = 1_000).execute(get(server))

        server.close()
        assertEquals(ActionOutcome.Failed(FailReason.TIMEOUT), outcome)
        assertTrue(seconds(started) < 3.0, "took ${seconds(started)} s")
    }

    @Test
    fun aServerThatNeverAnswersTimesOutAtTheDeadlineToo() = runBlocking {
        val server = dribblingServer("HTTP/1.1 204 No Content\r\n\r\n", gapMillis = 60_000)
        val started = System.nanoTime()

        val outcome = HttpRequestHandler(Dispatchers.IO, deadlineMillis = 700).execute(get(server))

        server.close()
        assertEquals(ActionOutcome.Failed(FailReason.TIMEOUT), outcome)
        assertTrue(seconds(started) < 3.0, "took ${seconds(started)} s")
    }

    @Test
    fun aPromptAnswerIsReportedWithinTheDeadline() = runBlocking {
        val ok = dribblingServer("HTTP/1.1 204 No Content\r\n\r\n", gapMillis = 0)
        val failing = dribblingServer("HTTP/1.1 500 Oops\r\n\r\n", gapMillis = 0)

        assertEquals(ActionOutcome.Done("204"), HttpRequestHandler(Dispatchers.IO).execute(get(ok)))
        assertEquals(ActionOutcome.Failed(FailReason.HTTP_STATUS, "500"), HttpRequestHandler(Dispatchers.IO).execute(get(failing)))
        ok.close()
        failing.close()
    }

    @Test
    fun aRefusedConnectionIsANetworkFailureNotATimeout() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }

        val outcome = HttpRequestHandler(Dispatchers.IO).execute(TaskAction.HttpRequest("http://127.0.0.1:$port/", HttpMethod.GET))

        assertEquals(ActionOutcome.Failed(FailReason.NETWORK), outcome)
    }
}
