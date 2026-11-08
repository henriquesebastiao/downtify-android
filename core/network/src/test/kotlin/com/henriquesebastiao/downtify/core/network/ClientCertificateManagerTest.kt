package com.henriquesebastiao.downtify.core.network

import com.henriquesebastiao.downtify.core.network.session.ClientCertificateManager
import com.henriquesebastiao.downtify.core.network.session.ClientCredentials
import com.henriquesebastiao.downtify.core.network.session.ClientKeyLoader
import java.security.KeyPairGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientCertificateManagerTest {

    @Test
    fun `presents the certificate to any host before pairing and only to the paired host after`() {
        assertTrue(ClientCertificateManager.allowsHost("music.example.com", pairedBaseUrl = null))
        assertTrue(
            ClientCertificateManager.allowsHost(
                peerHost = "music.example.com",
                pairedBaseUrl = "https://music.example.com/downtify",
            ),
        )
        assertTrue(
            ClientCertificateManager.allowsHost(
                peerHost = "MUSIC.EXAMPLE.COM",
                pairedBaseUrl = "https://music.example.com:8443",
            ),
        )
        assertTrue(
            ClientCertificateManager.allowsHost(
                peerHost = "music.example.com:443",
                pairedBaseUrl = "https://music.example.com",
            ),
        )
        assertFalse(
            ClientCertificateManager.allowsHost(
                peerHost = "cdn.thirdparty.example",
                pairedBaseUrl = "https://music.example.com",
            ),
        )
    }

    @Test
    fun `loads and caches the selected alias until cleared`() {
        val generatedKey = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.genKeyPair().private
        var alias: String? = "cloudflare-mtls"
        val baseUrl = "https://music.example.com"
        var keyLoads = 0

        val loader = ClientKeyLoader {
            keyLoads++
            ClientCredentials(generatedKey, emptyArray())
        }

        val manager = ClientCertificateManager(
            aliasProvider = { alias },
            baseUrlProvider = { baseUrl },
            loader = loader,
        )

        assertEquals("cloudflare-mtls", manager.chooseClientAlias(arrayOf("RSA"), null, null))
        assertSame(generatedKey, manager.getPrivateKey("cloudflare-mtls"))
        assertSame(generatedKey, manager.getPrivateKey("cloudflare-mtls"))
        assertEquals(1, keyLoads)

        manager.clearCache()
        assertSame(generatedKey, manager.getPrivateKey("cloudflare-mtls"))
        assertEquals(2, keyLoads)

        alias = null
        assertNull(manager.chooseClientAlias(arrayOf("RSA"), null, null))
    }
}
