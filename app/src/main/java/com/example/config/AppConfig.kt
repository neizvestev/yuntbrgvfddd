package com.example.config

import java.net.URI
import java.util.Locale

/**
 * Единая конфигурация мини-приложения T.GIT.
 *
 * Чтобы привязать приложение к вашему сайту, замените [TARGET_URL] ниже.
 */
object AppConfig {
    /**
     * Стартовая ссылка сайта, который открывается в приложении на весь экран.
     */
    const val TARGET_URL: String = "https://tgitmain.vercel.app/login"

    /**
     * Если false — разрешены переходы по всем веб-ссылкам (OAuth, CDN, внешние сервисы сайта)
     * внутри приложения без блокировки функций сайта.
     * Если true — переходы ограничены только доменом [TARGET_URL].
     */
    const val LOCK_TO_SINGLE_SITE: Boolean = false

    /**
     * Дополнительные разрешённые домены, если [LOCK_TO_SINGLE_SITE] включён.
     */
    val EXTRA_ALLOWED_HOSTS: Set<String> = emptySet()
}

/**
 * Политика проверки URL для веб-навигации внутри приложения.
 */
object UrlPolicy {

    fun extractBaseHost(url: String): String? {
        val rawHost = parseHost(url) ?: return null
        return rawHost.removePrefix("www.")
    }

    fun isUrlAllowed(
        candidateUrl: String,
        targetUrl: String = AppConfig.TARGET_URL,
        lockToSingleSite: Boolean = AppConfig.LOCK_TO_SINGLE_SITE,
        extraAllowedHosts: Set<String> = AppConfig.EXTRA_ALLOWED_HOSTS,
    ): Boolean {
        val trimmed = candidateUrl.trim()
        if (trimmed.equals("about:blank", ignoreCase = true) ||
            trimmed.startsWith("data:", ignoreCase = true) ||
            trimmed.startsWith("blob:", ignoreCase = true)
        ) {
            return true
        }

        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            return false
        }

        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return false
        if (scheme != "http" && scheme != "https") {
            return false
        }

        val candidateHost = uri.host?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() } ?: return false
        if (!lockToSingleSite) {
            return true
        }

        val baseTargetHost = extractBaseHost(targetUrl) ?: return false
        if (matchesHostOrSubdomain(candidateHost, baseTargetHost)) {
            return true
        }

        return extraAllowedHosts.any { allowed ->
            val cleanAllowed = allowed.trim().lowercase(Locale.ROOT).removePrefix("www.")
            cleanAllowed.isNotBlank() && matchesHostOrSubdomain(candidateHost, cleanAllowed)
        }
    }

    private fun parseHost(url: String): String? {
        return try {
            URI(url.trim()).host?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun matchesHostOrSubdomain(candidateHost: String, baseHost: String): Boolean {
        return candidateHost == baseHost ||
            candidateHost == "www.$baseHost" ||
            candidateHost.endsWith(".$baseHost")
    }
}
