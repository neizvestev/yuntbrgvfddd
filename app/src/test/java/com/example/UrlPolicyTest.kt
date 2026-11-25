package com.example

import com.example.config.AppConfig
import com.example.config.UrlPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlPolicyTest {

    @Test
    fun `default target url is tgitmain vercel app login and unlocked`() {
        assertEquals("https://tgitmain.vercel.app/login", AppConfig.TARGET_URL)
        assertFalse(AppConfig.LOCK_TO_SINGLE_SITE)
        assertTrue(UrlPolicy.isUrlAllowed("https://tgitmain.vercel.app/profile"))
        assertTrue(UrlPolicy.isUrlAllowed("https://accounts.google.com/signin"))
        assertTrue(UrlPolicy.isUrlAllowed("https://oauth.telegram.org/auth"))
    }

    @Test
    fun `allows web data and blob schemes`() {
        assertTrue(UrlPolicy.isUrlAllowed("about:blank"))
        assertTrue(UrlPolicy.isUrlAllowed("blob:https://tgitmain.vercel.app/1234"))
        assertTrue(UrlPolicy.isUrlAllowed("data:text/plain;base64,SGVsbG8="))
        assertFalse(UrlPolicy.isUrlAllowed("tg://resolve?domain=tgit"))
        assertFalse(UrlPolicy.isUrlAllowed("mailto:test@example.com"))
    }

    @Test
    fun `when locked allows target site root and subdomains only`() {
        val target = "https://tgitmain.vercel.app/login"
        assertTrue(
            UrlPolicy.isUrlAllowed(
                candidateUrl = "https://tgitmain.vercel.app/dashboard",
                targetUrl = target,
                lockToSingleSite = true,
            ),
        )
        assertFalse(
            UrlPolicy.isUrlAllowed(
                candidateUrl = "https://other.vercel.app/",
                targetUrl = target,
                lockToSingleSite = true,
            ),
        )
    }

    @Test
    fun `allows extra hosts when locked and explicitly configured`() {
        val target = "https://tgitmain.vercel.app/login"
        val extras = setOf("auth.example.org")
        assertTrue(
            UrlPolicy.isUrlAllowed(
                candidateUrl = "https://login.auth.example.org/oauth",
                targetUrl = target,
                lockToSingleSite = true,
                extraAllowedHosts = extras,
            ),
        )
    }
}
