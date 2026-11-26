package app.lodestone

import app.lodestone.data.Embeds
import app.lodestone.data.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EmbedsTest {
    private fun p(url: String) = Embeds.parse(url)

    @Test fun youtubeForms() {
        for (u in listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "https://youtu.be/dQw4w9WgXcQ?si=abc", "https://m.youtube.com/watch?v=dQw4w9WgXcQ&feature=share",
            "https://www.youtube.com/embed/dQw4w9WgXcQ", "https://www.youtube.com/live/dQw4w9WgXcQ?feature=share",
        )) {
            val e = p(u)!!
            assertEquals(u, Platform.YOUTUBE, e.platform)
            assertEquals("dQw4w9WgXcQ", e.id)
            assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", e.canonical)
        }
        assertEquals("https://www.youtube.com/shorts/abcdefghijk", p("https://www.youtube.com/shorts/abcdefghijk?feature=share")!!.canonical)
        assertNull(p("https://www.youtube.com/@somechannel"))
    }

    @Test fun instagramPostsReelsAndTrackingParams() {
        val a = p("https://www.instagram.com/p/CUbHfhpswxt/?utm_source=ig_web_copy_link&igsh=abc")!!
        assertEquals(Platform.INSTAGRAM, a.platform)
        assertEquals("https://www.instagram.com/p/CUbHfhpswxt/", a.canonical)
        assertEquals("https://www.instagram.com/p/CUbHfhpswxt/embed/captioned/", a.src)
        val r = p("https://www.instagram.com/reel/C8abcDEF123/?igsh=xyz")!!
        assertEquals("https://www.instagram.com/reel/C8abcDEF123/", r.canonical)
        assertEquals("CUbHfhpswxt", p("https://www.instagram.com/someuser/p/CUbHfhpswxt/")!!.id)
        assertNull(p("https://www.instagram.com/nasa/"))
    }

    @Test fun otherPlatforms() {
        assertEquals(Platform.TIKTOK, p("https://www.tiktok.com/@scout2015/video/6718335390845095173?is_from_webapp=1")!!.platform)
        assertEquals("https://x.com/jack/status/20", p("https://twitter.com/jack/status/20?s=20&t=abc")!!.canonical)
        assertEquals(Platform.X, p("https://x.com/jack/status/20")!!.platform)
        assertEquals("https://player.vimeo.com/video/76979871", p("https://vimeo.com/76979871")!!.src)
        assertEquals("https://open.spotify.com/embed/track/4uLU6hMCjMI75M1A2tKUQC", p("https://open.spotify.com/intl-de/track/4uLU6hMCjMI75M1A2tKUQC?si=zz")!!.src)
        assertEquals(Platform.SOUNDCLOUD, p("https://soundcloud.com/forss/flickermood")!!.platform)
        assertEquals(Platform.REDDIT, p("https://www.reddit.com/r/programming/comments/abc123/some_title/")!!.platform)
        assertEquals("https://embed.bsky.app/embed/bsky.app/app.bsky.feed.post/3l6oveex3ii2l", p("https://bsky.app/profile/bsky.app/post/3l6oveex3ii2l")!!.src)
        assertEquals(Platform.FACEBOOK, p("https://www.facebook.com/nasa/posts/10157123456789")!!.platform)
        assertEquals(Platform.PINTEREST, p("https://www.pinterest.com/pin/99360735500167749/")!!.platform)
        assertEquals(Platform.TWITCH, p("https://www.twitch.tv/videos/1234567890")!!.platform)
        assertEquals(Platform.DAILYMOTION, p("https://www.dailymotion.com/video/x8xvc4m")!!.platform)
        assertEquals(Platform.THREADS, p("https://www.threads.net/@zuck/post/CuXYZ12345")!!.platform)
        assertEquals(Platform.LOOM, p("https://www.loom.com/share/0123456789abcdef0123456789abcdef")!!.platform)
        assertEquals("https://embed.music.apple.com/us/album/test/123", p("https://music.apple.com/us/album/test/123")!!.src)
    }

    @Test fun ordinaryLinksAreNotEmbeds() {
        assertNull(p("https://en.wikipedia.org/wiki/Paris"))
        assertNull(p("not a url"))
        assertNull(p("ftp://youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test fun cleanStripsTrackingButKeepsRealParameters() {
        assertEquals("https://example.com/a?id=5", Embeds.clean("https://Example.com/a?utm_source=x&id=5&fbclid=zzz#frag"))
        assertEquals("https://example.com/a", Embeds.clean("https://example.com/a?utm_medium=email"))
        assertEquals("mailto:a@b.c", Embeds.clean("mailto:a@b.c"))
    }

    @Test fun duplicatesShareOneCanonicalLink() {
        val a = p("https://youtu.be/dQw4w9WgXcQ?si=abc")!!.canonical
        val b = p("https://www.youtube.com/watch?v=dQw4w9WgXcQ&feature=share")!!.canonical
        assertEquals(a, b)
        assertNotNull(Embeds.oembedUrl(p("https://youtu.be/dQw4w9WgXcQ")!!))
    }
}
