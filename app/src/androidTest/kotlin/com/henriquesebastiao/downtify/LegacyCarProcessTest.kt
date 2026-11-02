package com.henriquesebastiao.downtify

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Run on an emulator/scratch installation. No data reset and no phone Activity needed. */
@RunWith(AndroidJUnit4::class)
@androidx.media3.common.util.UnstableApi
class LegacyCarProcessTest {
    @Test fun coldRootAndRepeatedLegacyConnectionsAcrossProcesses() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bound = CountDownLatch(1)
        var remote: Messenger? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                remote = Messenger(service)
                bound.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) {
                remote = null
            }
        }
        assertTrue(
            context.bindService(
                Intent(context, LegacyCarProbeService::class.java),
                connection,
                Context.BIND_AUTO_CREATE,
            ),
        )
        try {
            assertTrue(bound.await(10, TimeUnit.SECONDS))
            repeat(30) {
                val answered = CountDownLatch(1)
                var connected = false
                var clientPid = Process.myPid()
                val reply = Messenger(
                    Handler(Looper.getMainLooper()) { response ->
                        connected = response.data.getBoolean("connected")
                        clientPid = response.data.getInt("pid")
                        answered.countDown()
                        true
                    },
                )
                checkNotNull(remote).send(Message.obtain().apply { replyTo = reply })
                assertTrue("Legacy browser timed out", answered.await(8, TimeUnit.SECONDS))
                assertTrue("Legacy root connection failed", connected)
                assertNotEquals(Process.myPid(), clientPid)
            }
        } finally {
            context.unbindService(connection)
        }
    }
}
