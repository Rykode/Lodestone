package app.lodestone.data

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

enum class Platform(val label: String) {
    YOUTUBE("YouTube"), INSTAGRAM("Instagram"), TIKTOK("TikTok"), X("X"), VIMEO("Vimeo"), SPOTIFY("Spotify"),
    SOUNDCLOUD("SoundCloud"), REDDIT("Reddit"), BLUESKY("Bluesky"), FACEBOOK("Facebook"), PINTEREST("Pinterest"),
    TWITCH("Twitch"), DAILYMOTION("Dailymotion"), THREADS("Threads"), LOOM("Loom"), APPLE_MUSIC("Apple Music"),
}

/**
 * A link recognised as a post or video on a known platform.
 * @param canonical the link with sharing and tracking junk removed (also used to spot duplicates)
 * @param src the platform's own embed address
 * @param aspect height divided by width of the embed
 * @param referer some players refuse to load without an identifying Referer
 */
data class Embed(val platform: Platform, val id: String, val canonical: String, val src: String, val aspect: Float, val referer: String? = null)

/** Recognises links to the major platforms and builds their embed addresses. Pure, so it is unit tested. */
object Embeds {
    private const val APP_SITE = "https://lodestone.app/"
    private const val APP_HOST = "lodestone.app"

    private val trackingParams = Regex("^(utm_.*|fbclid|gclid|igsh|igshid|si|feature|ref|ref_src|ref_url|s|t|pp|share_id|mc_.*|_hsenc|_hsmi|spm|cmpid)$", RegexOption.IGNORE_CASE)

    /** Strips tracking parameters and fragments. Anything that is not a plain http(s) link is returned untouched. */
    fun clean(url: String): String {
        val u = runCatching { URI(url.trim()) }.getOrNull() ?: return url
        if (u.scheme?.lowercase() !in setOf("http", "https") || u.host == null) return url
        val keep = (u.rawQuery ?: "").split('&').filter { it.isNotEmpty() && !trackingParams.matches(it.substringBefore('=')) }
        val b = StringBuilder().append(u.scheme.lowercase()).append("://").append(u.rawAuthority.lowercase()).append(u.rawPath ?: "")
        if (keep.isNotEmpty()) b.append('?').append(keep.joinToString("&"))
        return b.toString().removeSuffix("?")
    }

    private fun host(u: URI): String = (u.host ?: "").lowercase().removePrefix("www.").removePrefix("m.").removePrefix("mobile.")

    private fun query(u: URI): Map<String, String> = (u.rawQuery ?: "").split('&').filter { it.contains('=') }
        .associate { it.substringBefore('=') to runCatching { URLDecoder.decode(it.substringAfter('='), "UTF-8") }.getOrDefault("") }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun parse(url: String, dark: Boolean = true): Embed? {
        val u = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        if (u.scheme?.lowercase() !in setOf("http", "https")) return null
        val h = host(u)
        val parts = (u.path ?: "").split('/').filter { it.isNotEmpty() }
        val q = query(u)

        fun id(re: String, s: String) = Regex(re).matchEntire(s)?.value
        return when {
            // ---- YouTube
            h == "youtube.com" || h == "music.youtube.com" || h == "youtube-nocookie.com" || h == "youtu.be" -> {
                val vid = when {
                    h == "youtu.be" -> parts.firstOrNull()
                    parts.firstOrNull() in setOf("shorts", "embed", "live", "v") -> parts.getOrNull(1)
                    parts.firstOrNull() == "watch" -> q["v"]
                    else -> null
                }?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{6,15}")) } ?: return null
                val short = parts.firstOrNull() == "shorts"
                Embed(Platform.YOUTUBE, vid, "https://www.youtube.com/" + (if (short) "shorts/$vid" else "watch?v=$vid"),
                    "https://www.youtube.com/embed/$vid?playsinline=1&rel=0&modestbranding=1", if (short) 1.78f else 0.5625f, APP_SITE)
            }
            // ---- Instagram: /p/CODE, /reel/CODE, /tv/CODE (also /username/p/CODE)
            h == "instagram.com" || h == "instagr.am" -> {
                val i = parts.indexOfFirst { it in setOf("p", "reel", "reels", "tv") }
                val code = parts.getOrNull(i + 1)?.takeIf { i >= 0 && it.matches(Regex("[A-Za-z0-9_-]{5,20}")) } ?: return null
                val kind = if (parts[i] == "p") "p" else "reel"
                Embed(Platform.INSTAGRAM, code, "https://www.instagram.com/$kind/$code/", "https://www.instagram.com/$kind/$code/embed/captioned/", 1.45f, APP_SITE)
            }
            // ---- TikTok
            h == "tiktok.com" -> {
                val vid = parts.indexOf("video").let { if (it >= 0) parts.getOrNull(it + 1) else null }?.takeIf { it.matches(Regex("\\d{8,25}")) } ?: return null
                val user = parts.firstOrNull { it.startsWith("@") } ?: "@_"
                Embed(Platform.TIKTOK, vid, "https://www.tiktok.com/$user/video/$vid", "https://www.tiktok.com/embed/v2/$vid", 1.9f, APP_SITE)
            }
            // ---- X / Twitter
            h == "x.com" || h == "twitter.com" || h == "fxtwitter.com" || h == "vxtwitter.com" -> {
                val i = parts.indexOf("status").takeIf { it >= 0 } ?: parts.indexOf("statuses").takeIf { it >= 0 } ?: return null
                val tid = parts.getOrNull(i + 1)?.takeIf { it.matches(Regex("\\d{1,25}")) } ?: return null
                val user = parts.getOrNull(i - 1)?.takeIf { it != "i" } ?: "i"
                Embed(Platform.X, tid, "https://x.com/$user/status/$tid",
                    "https://platform.twitter.com/embed/Tweet.html?id=$tid&dnt=true&theme=${if (dark) "dark" else "light"}", 1.15f, APP_SITE)
            }
            // ---- Vimeo
            h == "vimeo.com" || h == "player.vimeo.com" -> {
                val vid = parts.lastOrNull { it.matches(Regex("\\d{5,12}")) } ?: return null
                Embed(Platform.VIMEO, vid, "https://vimeo.com/$vid", "https://player.vimeo.com/video/$vid", 0.5625f, APP_SITE)
            }
            // ---- Spotify
            h == "open.spotify.com" -> {
                val p = parts.filterNot { it.startsWith("intl-") }
                val type = p.getOrNull(0)?.takeIf { it in setOf("track", "album", "playlist", "episode", "show", "artist") } ?: return null
                val sid = p.getOrNull(1)?.takeIf { it.matches(Regex("[A-Za-z0-9]{10,30}")) } ?: return null
                Embed(Platform.SPOTIFY, sid, "https://open.spotify.com/$type/$sid", "https://open.spotify.com/embed/$type/$sid", if (type == "track" || type == "episode") 0.44f else 1.15f, APP_SITE)
            }
            // ---- SoundCloud
            h == "soundcloud.com" && parts.size >= 2 && parts[0] !in setOf("discover", "you", "upload", "stream", "search", "pages", "settings") -> {
                val canon = "https://soundcloud.com/" + parts.take(if (parts.getOrNull(1) == "sets") 3 else 2).joinToString("/")
                Embed(Platform.SOUNDCLOUD, parts.drop(1).joinToString("/"), canon, "https://w.soundcloud.com/player/?url=${enc(canon)}&auto_play=false&visual=false&show_comments=false", 0.5f, APP_SITE)
            }
            // ---- Reddit
            h == "reddit.com" || h == "old.reddit.com" -> {
                val i = parts.indexOf("comments").takeIf { it >= 0 && parts.firstOrNull() == "r" } ?: return null
                val pid = parts.getOrNull(i + 1)?.takeIf { it.matches(Regex("[a-z0-9]{4,10}")) } ?: return null
                val path = parts.take(i + 3).joinToString("/")
                Embed(Platform.REDDIT, pid, "https://www.reddit.com/$path/", "https://embed.reddit.com/$path/?embed=true&theme=${if (dark) "dark" else "light"}", 1.0f, APP_SITE)
            }
            // ---- Bluesky
            h == "bsky.app" -> {
                val i = parts.indexOf("profile").takeIf { it >= 0 && parts.getOrNull(it + 2) == "post" } ?: return null
                val handle = parts[i + 1]; val rkey = parts.getOrNull(i + 3) ?: return null
                Embed(Platform.BLUESKY, rkey, "https://bsky.app/profile/$handle/post/$rkey", "https://embed.bsky.app/embed/$handle/app.bsky.feed.post/$rkey", 0.9f, APP_SITE)
            }
            // ---- Facebook
            h == "facebook.com" || h == "fb.watch" || h == "fb.com" -> {
                val isVideo = h == "fb.watch" || parts.any { it in setOf("videos", "watch", "reel", "reels") } || q.containsKey("v")
                val canon = clean(url)
                if (parts.isEmpty() && h != "fb.watch") return null
                val href = enc(canon)
                val src = if (isVideo) "https://www.facebook.com/plugins/video.php?href=$href&show_text=true&width=500" else "https://www.facebook.com/plugins/post.php?href=$href&show_text=true&width=500"
                Embed(Platform.FACEBOOK, parts.lastOrNull() ?: h, canon, src, if (isVideo) 0.7f else 1.2f, APP_SITE)
            }
            // ---- Pinterest
            h.endsWith("pinterest.com") || h == "pin.it" -> {
                val pid = parts.indexOf("pin").let { if (it >= 0) parts.getOrNull(it + 1) else null }?.takeIf { it.matches(Regex("\\d{6,25}")) } ?: return null
                Embed(Platform.PINTEREST, pid, "https://www.pinterest.com/pin/$pid/", "https://assets.pinterest.com/ext/embed.html?id=$pid", 1.4f, APP_SITE)
            }
            // ---- Twitch
            h == "twitch.tv" || h == "clips.twitch.tv" -> {
                when {
                    h == "clips.twitch.tv" && parts.isNotEmpty() ->
                        Embed(Platform.TWITCH, parts[0], "https://clips.twitch.tv/${parts[0]}", "https://clips.twitch.tv/embed?clip=${parts[0]}&parent=$APP_HOST&autoplay=false", 0.5625f, APP_SITE)
                    parts.getOrNull(0) == "videos" && parts.getOrNull(1)?.matches(Regex("\\d+")) == true ->
                        Embed(Platform.TWITCH, parts[1], "https://www.twitch.tv/videos/${parts[1]}", "https://player.twitch.tv/?video=v${parts[1]}&parent=$APP_HOST&autoplay=false", 0.5625f, APP_SITE)
                    parts.getOrNull(1) == "clip" && parts.size >= 3 ->
                        Embed(Platform.TWITCH, parts[2], "https://clips.twitch.tv/${parts[2]}", "https://clips.twitch.tv/embed?clip=${parts[2]}&parent=$APP_HOST&autoplay=false", 0.5625f, APP_SITE)
                    parts.size == 1 && parts[0] !in setOf("directory", "videos", "downloads", "jobs", "turbo", "settings") ->
                        Embed(Platform.TWITCH, parts[0], "https://www.twitch.tv/${parts[0]}", "https://player.twitch.tv/?channel=${parts[0]}&parent=$APP_HOST&autoplay=false", 0.5625f, APP_SITE)
                    else -> return null
                }
            }
            // ---- Dailymotion
            h == "dailymotion.com" || h == "dai.ly" -> {
                val vid = (if (h == "dai.ly") parts.firstOrNull() else parts.getOrNull(parts.indexOf("video") + 1).takeIf { parts.contains("video") })?.substringBefore('_')
                    ?.takeIf { it.matches(Regex("[a-z0-9]{5,10}")) } ?: return null
                Embed(Platform.DAILYMOTION, vid, "https://www.dailymotion.com/video/$vid", "https://geo.dailymotion.com/player.html?video=$vid", 0.5625f, APP_SITE)
            }
            // ---- Threads
            h == "threads.net" || h == "threads.com" -> {
                val i = parts.indexOf("post").takeIf { it >= 1 } ?: return null
                val user = parts[i - 1]; val code = parts.getOrNull(i + 1) ?: return null
                Embed(Platform.THREADS, code, "https://www.threads.net/$user/post/$code", "https://www.threads.net/$user/post/$code/embed", 1.3f, APP_SITE)
            }
            // ---- Loom
            h == "loom.com" -> {
                val i = parts.indexOfFirst { it == "share" || it == "embed" }.takeIf { it >= 0 } ?: return null
                val lid = parts.getOrNull(i + 1)?.takeIf { it.matches(Regex("[a-f0-9]{20,40}")) } ?: return null
                Embed(Platform.LOOM, lid, "https://www.loom.com/share/$lid", "https://www.loom.com/embed/$lid", 0.5625f, APP_SITE)
            }
            // ---- Apple Music
            h == "music.apple.com" && parts.size >= 2 ->
                Embed(Platform.APPLE_MUSIC, parts.last(), clean(url), clean(url).replaceFirst("://music.apple.com", "://embed.music.apple.com"), 1.1f, APP_SITE)
            else -> null
        }
    }

    /** Public oEmbed address for a title, author and thumbnail, or null where the platform needs a login or a key. */
    fun oembedUrl(e: Embed): String? {
        val c = enc(e.canonical)
        return when (e.platform) {
            Platform.YOUTUBE -> "https://www.youtube.com/oembed?format=json&url=$c"
            Platform.VIMEO -> "https://vimeo.com/api/oembed.json?url=$c"
            Platform.SPOTIFY -> "https://open.spotify.com/oembed?url=$c"
            Platform.SOUNDCLOUD -> "https://soundcloud.com/oembed?format=json&url=$c"
            Platform.TIKTOK -> "https://www.tiktok.com/oembed?url=$c"
            Platform.X -> "https://publish.twitter.com/oembed?omit_script=true&url=$c"
            Platform.BLUESKY -> "https://embed.bsky.app/oembed?url=$c"
            Platform.REDDIT -> "https://www.reddit.com/oembed?url=$c"
            Platform.DAILYMOTION -> "https://www.dailymotion.com/services/oembed?format=json&url=$c"
            Platform.PINTEREST -> "https://www.pinterest.com/oembed.json?url=$c"
            Platform.LOOM -> "https://www.loom.com/v1/oembed?url=$c"
            Platform.TWITCH, Platform.INSTAGRAM, Platform.FACEBOOK, Platform.THREADS, Platform.APPLE_MUSIC -> null
        }
    }

    /** True where the page itself hides everything behind a login and the embed has to be read instead. */
    fun needsEmbedScrape(e: Embed): Boolean = e.platform == Platform.INSTAGRAM || e.platform == Platform.THREADS
}
