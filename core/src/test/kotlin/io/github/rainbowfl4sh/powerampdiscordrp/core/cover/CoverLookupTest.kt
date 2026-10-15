package io.github.rainbowfl4sh.powerampdiscordrp.core.cover

import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.FakeNet.Companion.bytes
import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.FakeNet.Companion.json
import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.FakeNet.Companion.status
import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.FakeNet.Companion.text
import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.Samples.ENCODED_JPEG
import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.Samples.JPEG
import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.Samples.NO_SOURCES
import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.Samples.TRACK
import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.Samples.UPLOAD_ONLY
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import okhttp3.Credentials
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CoverLookupTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val net = FakeNet()
    private val clock = FakeClock()
    private val log = RecordingLogger()

    private fun resolver() = CoverResolver(
        http = net.client,
        cacheFile = File(tmp.root, "covers.tsv"),
        local = { JPEG },
        encoder = { _, _ -> ENCODED_JPEG },
        clock = clock,
        log = log,
        endpoints = net.endpoints(),
    )

    @After
    fun tearDown() = net.close()

    // ---------------------------------------------------------------- realistic answers (shortened)

    private fun deezerAlbum(coverXl: String, coverBig: String = "") = """
        {"data":[{"id":302127,"title":"Discovery","link":"https://www.deezer.com/album/302127",
        "cover":"https://api.deezer.com/album/302127/image",
        "cover_small":"https://cdn-images.dzcdn.net/images/cover/2e018122cb56986277102d2041a592c8/56x56-000000-80-0-0.jpg",
        "cover_big":"$coverBig","cover_xl":"$coverXl","md5_image":"2e018122cb56986277102d2041a592c8","genre_id":113,
        "nb_tracks":14,"record_type":"album","explicit_lyrics":false,
        "artist":{"id":27,"name":"Daft Punk","link":"https://www.deezer.com/artist/27","type":"artist"},"type":"album"}],
        "total":1,"next":"https://api.deezer.com/search/album?q=Discovery&limit=1&index=1"}
    """.trimIndent()

    private fun deezerTrack(coverXl: String) = """
        {"data":[{"id":3135556,"readable":true,"title":"One More Time","duration":320,"rank":858143,
        "artist":{"id":27,"name":"Daft Punk","type":"artist"},
        "album":{"id":302127,"title":"Discovery","cover":"https://api.deezer.com/album/302127/image",
        "cover_big":"https://cdn-images.dzcdn.net/images/cover/abc/500x500-000000-80-0-0.jpg","cover_xl":"$coverXl",
        "md5_image":"abc","type":"album"},"type":"track"}],"total":12}
    """.trimIndent()

    private fun itunes(artist: String, art: String = "https://is1-ssl.mzstatic.com/image/thumb/Music/v4/2e/01/18/source/100x100bb.jpg") = """
        {"resultCount":1,"results":[{"wrapperType":"collection","collectionType":"Album","artistId":5468295,
        "collectionId":697194953,"artistName":"$artist","collectionName":"Discovery",
        "artworkUrl60":"https://is1-ssl.mzstatic.com/image/thumb/Music/v4/2e/01/18/source/60x60bb.jpg",
        "artworkUrl100":"$art","collectionPrice":9.99,"trackCount":14,"country":"USA","currency":"USD",
        "releaseDate":"2001-03-07T08:00:00Z","primaryGenreName":"Electronic"}]}
    """.trimIndent()

    private fun bandcamp(band: String, img: String) = """
        {"auto":{"results":[{"type":"a","id":2148357914,"art_id":3456789012,"img_id":null,"name":"Discovery",
        "band_id":12345,"band_name":"$band","url":"https://daftpunk.bandcamp.com/album/discovery","img":"$img",
        "tag_names":null,"is_label":false,"stat_params_for_tag":"?from=search"}],"stat_params_for_tag":"?from=search","time_ms":21}}
    """.trimIndent()

    private val mbReleaseGroup = """
        {"created":"2026-10-06T10:00:00.000Z","count":3,"offset":0,"release-groups":[{"id":"48117b81-d97b-3ac2-8ee6-50e1c4e6e2d9",
        "type-id":"f529b476-6e62-324f-b0aa-1f3e33d313fc","score":100,"primary-type-id":"f529b476-6e62-324f-b0aa-1f3e33d313fc",
        "count":12,"title":"Discovery","first-release-date":"2001-02-26","primary-type":"Album",
        "artist-credit":[{"name":"Daft Punk","artist":{"id":"056e4f3e-d505-4dad-8ec1-d04f521cbb56","name":"Daft Punk","sort-name":"Daft Punk"}}],
        "releases":[{"id":"9e2c3a1b-0000-4000-8000-000000000001","status-id":"4e304316-386d-3409-af2e-78857eec5cfe","title":"Discovery","status":"Official"}]}]}
    """.trimIndent()

    private val caaRelease = """
        {"images":[
         {"approved":true,"back":true,"comment":"","edit":1,"front":false,"id":111,"image":"http://coverartarchive.org/release/r/111.jpg",
          "thumbnails":{"250":"http://coverartarchive.org/release/r/111-250.jpg","500":"http://coverartarchive.org/release/r/111-500.jpg","large":"http://coverartarchive.org/release/r/111-500.jpg"},"types":["Back"]},
         {"approved":true,"back":false,"comment":"","edit":2,"front":true,"id":222,"image":"http://coverartarchive.org/release/r/222.jpg",
          "thumbnails":{"250":"http://coverartarchive.org/release/r/222-250.jpg","500":"http://coverartarchive.org/release/r/222-500.jpg",
          "1200":"http://coverartarchive.org/release/r/222-1200.jpg","large":"http://coverartarchive.org/release/r/222-500.jpg",
          "small":"http://coverartarchive.org/release/r/222-250.jpg"},"types":["Front"]}],
        "release":"https://musicbrainz.org/release/9e2c3a1b-0000-4000-8000-000000000001"}
    """.trimIndent()

    private fun discogs(title: String, cover: String) = """
        {"pagination":{"page":1,"pages":25,"per_page":1,"items":25,"urls":{"last":"https://api.discogs.com/database/search?page=25","next":"https://api.discogs.com/database/search?page=2"}},
        "results":[{"country":"Europe","year":"2001","format":["CD","Album"],"label":["Virgin"],"type":"release",
        "genre":["Electronic"],"style":["House","Disco"],"id":28233,"barcode":["724384960650"],
        "user_data":{"in_wantlist":false,"in_collection":false},"master_id":5893,"master_url":"https://api.discogs.com/masters/5893",
        "uri":"/release/28233-Daft-Punk-Discovery","catno":"7243 8 49606 5 0","title":"$title",
        "thumb":"https://i.discogs.com/thumb/R-28233.jpeg","cover_image":"$cover","resource_url":"https://api.discogs.com/releases/28233",
        "community":{"want":3021,"have":17000}}]}
    """.trimIndent()

    private val spotifyToken = """{"access_token":"BQDtoken1","token_type":"Bearer","expires_in":3600}"""

    private fun spotifyAlbums(artist: String) = """
        {"albums":{"href":"https://api.spotify.com/v1/search?query=x&type=album&offset=0&limit=1","items":[{"album_type":"album",
        "artists":[{"external_urls":{"spotify":"https://open.spotify.com/artist/4tZwfgrHOc3mvqYlEYSvVi"},"href":"https://api.spotify.com/v1/artists/4tZwfgrHOc3mvqYlEYSvVi",
        "id":"4tZwfgrHOc3mvqYlEYSvVi","name":"$artist","type":"artist","uri":"spotify:artist:4tZwfgrHOc3mvqYlEYSvVi"}],
        "external_urls":{"spotify":"https://open.spotify.com/album/2noRn2Aes5aoNVsU6iWThc"},"id":"2noRn2Aes5aoNVsU6iWThc",
        "images":[{"height":640,"url":"https://i.scdn.co/image/ab67616d0000b273da5d5aeeabacacc1263c0f4b","width":640},
        {"height":300,"url":"https://i.scdn.co/image/ab67616d00001e02da5d5aeeabacacc1263c0f4b","width":300}],
        "name":"Discovery","release_date":"2001-03-12","total_tracks":14,"type":"album"}],"limit":1,"offset":0,"previous":null,"total":800}}
    """.trimIndent()

    // ---------------------------------------------------------------- Deezer

    @Test
    fun deezerAlbumSearch() {
        val cover = "https://cdn-images.dzcdn.net/images/cover/2e018122cb56986277102d2041a592c8/1000x1000-000000-80-0-0.jpg"
        net.on("/deezer/search/album") { json(deezerAlbum(cover)) }

        val r = resolver().resolve(TRACK, NO_SOURCES.copy(srcDeezer = true))

        assertEquals(cover, r.url)
        assertEquals("Deezer", r.source)
        assertFalse(r.isUploaded)
        val req = net.last("/deezer/search/album")
        assertEquals("1", req.url.queryParameter("limit"))
        assertEquals("artist:\"Daft Punk\" album:\"Discovery\"", req.url.queryParameter("q"))
        assertTrue(log.has("Cover found on Deezer"))
    }

    @Test
    fun deezerPlaceholderFallsBackToTrackSearch() {
        net.on("/deezer/search/album") { json(deezerAlbum("https://cdn-images.dzcdn.net/images/cover//1000x1000-000000-80-0-0.jpg")) }
        net.on("/deezer/search/track") { json(deezerTrack("https://cdn-images.dzcdn.net/images/cover/abc/1000x1000-000000-80-0-0.jpg")) }

        val r = resolver().resolve(TRACK, NO_SOURCES.copy(srcDeezer = true))

        assertEquals("https://cdn-images.dzcdn.net/images/cover/abc/1000x1000-000000-80-0-0.jpg", r.url)
        assertEquals("artist:\"Daft Punk\" track:\"One More Time\"", net.last("/deezer/search/track").url.queryParameter("q"))
    }

    @Test
    fun deezerUsesCoverBigAndFreeTextAsLastTry() {
        var albumCalls = 0
        net.on("/deezer/search/album") {
            albumCalls++
            if (albumCalls == 1) json("""{"data":[],"total":0}""")
            else json(deezerAlbum(coverXl = "", coverBig = "https://cdn-images.dzcdn.net/images/cover/abc/500x500.jpg"))
        }
        net.on("/deezer/search/track") { json("""{"data":[],"total":0}""") }

        val r = resolver().resolve(TRACK, NO_SOURCES.copy(srcDeezer = true))

        assertEquals("https://cdn-images.dzcdn.net/images/cover/abc/500x500.jpg", r.url)
        assertEquals("Daft Punk Discovery", net.last("/deezer/search/album").url.queryParameter("q"))
        assertEquals(3, net.requests.size)
    }

    @Test
    fun deezerRejectsOtherArtistsInEveryQuery() {
        // real answers: artist:"DJ Bobo" album:"Christmas" -> Various Artists; the free text finds anyone's album
        var albumCalls = 0
        net.on("/deezer/search/album") {
            albumCalls++
            val other = if (albumCalls == 1) "Various Artists" else "Oliver Mascarenhas"
            json(deezerAlbum("https://cdn-images.dzcdn.net/images/cover/x$albumCalls/1000x1000.jpg").replace("\"name\":\"Daft Punk\"", "\"name\":\"$other\""))
        }
        net.on("/deezer/search/track") {
            json(deezerTrack("https://cdn-images.dzcdn.net/images/cover/t/1000x1000.jpg").replace("\"name\":\"Daft Punk\"", "\"name\":\"Dj Master Vip\""))
        }

        val r = resolver().resolve(TRACK, NO_SOURCES.copy(srcDeezer = true))

        assertTrue(r.isEmpty)
        assertEquals("album, track and free text were asked", 3, net.requests.size)
    }

    @Test
    fun deezerTakesTheSameArtistInAnotherSpelling() {
        val cover = "https://cdn-images.dzcdn.net/images/cover/dc/1000x1000-000000-80-0-0.jpg"
        net.on("/deezer/search/album") { json(deezerAlbum(cover).replace("\"name\":\"Daft Punk\"", "\"name\":\"Destiny's Child\"")) }

        // Picard / MusicBrainz tags use the typographic apostrophe
        val r = resolver().resolve(TRACK.copy(artist = "Destiny’s Child", album = "Survivor"), NO_SOURCES.copy(srcDeezer = true))

        assertEquals(cover, r.url)
    }

    @Test
    fun aRequestThatCannotBeBuiltOnlyFailsItsSource() {
        // a token OkHttp refuses in a header (here: a line break) used to end the whole lookup
        net.on("/spotify-accounts/api/token", "POST") { json("""{"access_token":"tok\nen","token_type":"Bearer","expires_in":3600}""") }
        val cover = "https://cdn-images.dzcdn.net/images/cover/2e01/1000x1000-000000-80-0-0.jpg"
        net.on("/deezer/search/album") { json(deezerAlbum(cover)) }

        val r = resolver().resolve(TRACK, spotifyConfig.copy(srcDeezer = true))

        assertEquals("Deezer", r.source)
        assertEquals(0, net.count("/spotify-api/v1/search"))
        assertTrue(log.has("Spotify: request cannot be built"))
        assertFalse("the token stays out of the log", log.lines.any { it.contains("tok\nen") || it.contains("Bearer") })
    }

    @Test
    fun keysArePastedWithoutInvisibleCharacters() {
        net.on("/discogs/database/search") { json(discogs("Daft Punk - Discovery", "https://i.discogs.com/x.jpeg")) }
        net.on("/spotify-accounts/api/token", "POST") { status(400) }

        resolver().resolve(TRACK, spotifyConfig.copy(spotifyId = "​client-id ", spotifySecret = "s3 cr3t﻿",
            srcDiscogs = true, discogsToken = "﻿tok​en"))

        assertEquals(Credentials.basic("client-id", "s3cr3t"), net.last("/spotify-accounts/api/token").headers["Authorization"])
        assertEquals("token", net.last("/discogs/database/search").url.queryParameter("token"))
    }

    @Test
    fun quotesAreRemovedFromQueries() {
        net.on("/deezer/search/album") { json("""{"data":[]}""") }
        net.on("/deezer/search/track") { json("""{"data":[]}""") }

        resolver().resolve(TRACK.copy(album = "The \"Best\" Of "), NO_SOURCES.copy(srcDeezer = true))

        assertEquals("artist:\"Daft Punk\" album:\"The  Best  Of\"", net.requests.first().url.queryParameter("q"))
    }

    // ---------------------------------------------------------------- iTunes

    @Test
    fun itunesAlbumSearchUsesTheBigArtwork() {
        net.on("/itunes/search") { json(itunes("Daft Punk")) }

        val r = resolver().resolve(TRACK, NO_SOURCES.copy(srcItunes = true))

        assertEquals("https://is1-ssl.mzstatic.com/image/thumb/Music/v4/2e/01/18/source/600x600bb.jpg", r.url)
        assertEquals("iTunes", r.source)
        val req = net.last("/itunes/search")
        assertEquals("music", req.url.queryParameter("media"))
        assertEquals("album", req.url.queryParameter("entity"))
        assertEquals("Daft Punk Discovery", req.url.queryParameter("term"))
        assertTrue("encoded like the AIMP plugin", req.url.encodedQuery!!.endsWith("term=Daft%20Punk%20Discovery"))
    }

    @Test
    fun itunesRejectsAnotherArtist() {
        net.on("/itunes/search") { json(itunes("Some Tribute Orchestra")) }

        val r = resolver().resolve(TRACK, NO_SOURCES.copy(srcItunes = true))

        assertTrue(r.isEmpty)
        // album and song were both asked
        assertEquals(listOf("album", "song"), net.requests.map { it.url.queryParameter("entity") })
    }

    // ---------------------------------------------------------------- Bandcamp

    @Test
    fun bandcampSearchAsksForTheFullSizeImage() {
        net.on("/bandcamp/api/bcsearch_public_api/1/autocomplete_elastic", "POST") {
            json(bandcamp("Daft Punk", "http://f4.bcbits.com/img/a3456789012_3.jpg"))
        }

        val r = resolver().resolve(TRACK, NO_SOURCES.copy(srcBandcamp = true))

        assertEquals("https://f4.bcbits.com/img/a3456789012_10.jpg", r.url)
        assertEquals("Bandcamp", r.source)
        val req = net.last("/bandcamp/api/bcsearch_public_api/1/autocomplete_elastic")
        assertEquals("application/json", req.headers["Content-Type"])
        assertEquals(
            """{"search_text":"Daft Punk Discovery","search_filter":"a","full_page":false,"fan_id":null}""",
            req.body!!.utf8(),
        )
    }

    @Test
    fun bandcampFallsBackToTracksAndChecksTheArtist() {
        net.on("/bandcamp/api/bcsearch_public_api/1/autocomplete_elastic", "POST") { req ->
            if (req.body!!.utf8().contains("\"search_filter\":\"a\"")) json(bandcamp("Someone Else", "https://f4.bcbits.com/img/a1_3.jpg"))
            else json(bandcamp("DAFT PUNK", "https://f4.bcbits.com/img/a2_3.png"))
        }

        val r = resolver().resolve(TRACK, NO_SOURCES.copy(srcBandcamp = true))

        assertEquals("https://f4.bcbits.com/img/a2_10.png", r.url)
        assertTrue(net.last("/bandcamp/api/bcsearch_public_api/1/autocomplete_elastic").body!!.utf8()
            .contains("\"search_text\":\"Daft Punk One More Time\",\"search_filter\":\"t\""))
    }

    @Test
    fun bandcampNeedsAnArtist() {
        // Poweramp sends "" for an untagged artist: " Greatest Hits" would find any band's album
        net.on("/bandcamp/api/bcsearch_public_api/1/autocomplete_elastic", "POST") {
            json(bandcamp("Owane", "https://f4.bcbits.com/img/a1_3.jpg"))
        }

        assertTrue(resolver().resolve(TRACK.copy(artist = "", album = "Greatest Hits"), NO_SOURCES.copy(srcBandcamp = true)).isEmpty)
        assertEquals(0, net.requests.size)
    }

    @Test
    fun bandcampImageWithoutSizeSuffixIsIgnored() {
        net.on("/bandcamp/api/bcsearch_public_api/1/autocomplete_elastic", "POST") {
            json(bandcamp("Daft Punk", "https://f4.bcbits.com/img/blank.jpg"))
        }

        assertTrue(resolver().resolve(TRACK, NO_SOURCES.copy(srcBandcamp = true)).isEmpty)
    }

    // ---------------------------------------------------------------- MusicBrainz + Cover Art Archive

    @Test
    fun musicBrainzReleaseGroupAndCoverArtArchive() {
        net.on("/mb/ws/2/release-group/") { json(mbReleaseGroup) }
        net.on("/caa/release-group/48117b81-d97b-3ac2-8ee6-50e1c4e6e2d9") { json(caaRelease) }

        val r = resolver().resolve(TRACK, NO_SOURCES.copy(srcMusicBrainz = true))

        assertEquals("the front image, upgraded to https", "https://coverartarchive.org/release/r/222-500.jpg", r.url)
        assertEquals("MusicBrainz", r.source)
        val mb = net.last("/mb/ws/2/release-group/")
        assertEquals("json", mb.url.queryParameter("fmt"))
        assertEquals("releasegroup:\"Discovery\" AND artist:\"Daft Punk\"", mb.url.queryParameter("query"))
        assertEquals("application/json", mb.headers["Accept"])
        assertEquals(Web.USER_AGENT, mb.headers["User-Agent"])
        assertTrue(mb.headers["User-Agent"]!!.startsWith("PowerampDiscordRP/1.0 ( https://github.com/RainBowFl4sh/Poweramp-Discord-RP )"))
    }

    @Test
    fun musicBrainzRecordingSearchWithoutAlbum() {
        net.on("/mb/ws/2/recording/") {
            json("""{"created":"x","count":1,"offset":0,"recordings":[{"id":"rec-1","score":95,"title":"One More Time","length":320000,
                "artist-credit":[{"name":"Daft Punk","artist":{"id":"056e","name":"Daft Punk"}}],
                "releases":[{"id":"rel-7","count":1,"title":"Discovery","status":"Official"}]}]}""")
        }
        net.on("/caa/release/rel-7") {
            json("""{"images":[{"front":true,"thumbnails":{"large":"http://coverartarchive.org/release/rel-7/9-500.jpg"}}],"release":"x"}""")
        }

        val r = resolver().resolve(TRACK.copy(album = ""), NO_SOURCES.copy(srcMusicBrainz = true))

        assertEquals("https://coverartarchive.org/release/rel-7/9-500.jpg", r.url)
        assertEquals("recording:\"One More Time\" AND artist:\"Daft Punk\"", net.last("/mb/ws/2/recording/").url.queryParameter("query"))
    }

    @Test
    fun musicBrainzWeakMatchIsRejected() {
        net.on("/mb/ws/2/release-group/") { json(mbReleaseGroup.replace("\"score\":100", "\"score\":72")) }

        assertTrue(resolver().resolve(TRACK, NO_SOURCES.copy(srcMusicBrainz = true)).isEmpty)
        assertEquals(0, net.requests.count { it.url.encodedPath.startsWith("/caa/") })
    }

    @Test
    fun musicBrainzNeedsAnArtist() {
        assertTrue(resolver().resolve(TRACK.copy(artist = ""), NO_SOURCES.copy(srcMusicBrainz = true)).isEmpty)
        assertEquals(0, net.requests.size)
    }

    // ---------------------------------------------------------------- Discogs

    @Test
    fun discogsSearchWithToken() {
        net.on("/discogs/database/search") {
            json(discogs("Daft Punk - Discovery", "http://i.discogs.com/abc/rs:fit/g:sm/q:90/h:600/w:600/R-28233.jpeg"))
        }

        val r = resolver().resolve(TRACK, NO_SOURCES.copy(srcDiscogs = true, discogsToken = " tok/en+1 "))

        assertEquals("https://i.discogs.com/abc/rs:fit/g:sm/q:90/h:600/w:600/R-28233.jpeg", r.url)
        assertEquals("Discogs", r.source)
        val req = net.last("/discogs/database/search")
        assertEquals("release", req.url.queryParameter("type"))
        assertEquals("Daft Punk", req.url.queryParameter("artist"))
        assertEquals("Discovery", req.url.queryParameter("release_title"))
        assertEquals("tok/en+1", req.url.queryParameter("token"))
        assertEquals(null, req.url.queryParameter("track"))
    }

    @Test
    fun discogsRejectsOtherArtistsAndPlaceholders() {
        net.on("/discogs/database/search") { json(discogs("Daft Punk - Discovery", "https://s.discogs.com/images/spacer.gif")) }
        assertTrue(resolver().resolve(Samples.album(1), NO_SOURCES.copy(srcDiscogs = true, discogsToken = "t")).isEmpty)

        net.on("/discogs/database/search") { json(discogs("Justice - Cross", "https://i.discogs.com/x.jpeg")) }
        assertTrue(resolver().resolve(Samples.album(2), NO_SOURCES.copy(srcDiscogs = true, discogsToken = "t")).isEmpty)
    }

    @Test
    fun discogsWithoutTokenIsNotAsked() {
        assertTrue(resolver().resolve(TRACK, NO_SOURCES.copy(srcDiscogs = true, discogsToken = "")).isEmpty)
        assertEquals(0, net.requests.size)
    }

    @Test
    fun discogsHttpErrorIsLogged() {
        net.on("/discogs/database/search") { status(401, """{"message": "You must authenticate to access this resource."}""") }

        assertTrue(resolver().resolve(TRACK, NO_SOURCES.copy(srcDiscogs = true, discogsToken = "bad")).isEmpty)
        assertTrue(log.has("Discogs lookup failed (HTTP 401)"))
    }

    // ---------------------------------------------------------------- Spotify

    private val spotifyConfig = NO_SOURCES.copy(srcSpotify = true, spotifyId = "client-id", spotifySecret = "s3cr3t")

    @Test
    fun spotifyClientCredentialsAndAlbumSearch() {
        net.on("/spotify-accounts/api/token", "POST") { json(spotifyToken) }
        net.on("/spotify-api/v1/search") { json(spotifyAlbums("Daft Punk")) }

        val r = resolver().resolve(TRACK, spotifyConfig)

        assertEquals("https://i.scdn.co/image/ab67616d0000b273da5d5aeeabacacc1263c0f4b", r.url)
        assertEquals("Spotify", r.source)
        val token = net.last("/spotify-accounts/api/token")
        assertEquals(Credentials.basic("client-id", "s3cr3t"), token.headers["Authorization"])
        assertEquals("grant_type=client_credentials", token.body!!.utf8())
        assertTrue(token.headers["Content-Type"]!!.startsWith("application/x-www-form-urlencoded"))
        val search = net.last("/spotify-api/v1/search")
        assertEquals("Bearer BQDtoken1", search.headers["Authorization"])
        assertEquals("album", search.url.queryParameter("type"))
        assertEquals("album:Discovery artist:Daft Punk", search.url.queryParameter("q"))
    }

    @Test
    fun spotifyTrackSearchChecksTheTrackArtist() {
        net.on("/spotify-accounts/api/token", "POST") { json(spotifyToken) }
        net.on("/spotify-api/v1/search") { req ->
            if (req.url.queryParameter("type") == "album") json("""{"albums":{"items":[],"total":0}}""")
            else json("""{"tracks":{"items":[{"album":{"album_type":"compilation","artists":[{"name":"Various Artists"}],
                "images":[{"height":640,"url":"https://i.scdn.co/image/compilation640","width":640}],"name":"Hits 2001"},
                "artists":[{"name":"Daft Punk"}],"name":"One More Time","type":"track"}],"total":1}}""")
        }

        val r = resolver().resolve(TRACK, spotifyConfig)

        assertEquals("https://i.scdn.co/image/compilation640", r.url)
        assertEquals("track:One More Time artist:Daft Punk", net.last("/spotify-api/v1/search").url.queryParameter("q"))
    }

    @Test
    fun spotifyTokenIsCachedUntilItExpires() {
        net.on("/spotify-accounts/api/token", "POST") { json(spotifyToken) }
        net.on("/spotify-api/v1/search") { json(spotifyAlbums("Daft Punk")) }
        val resolver = resolver()

        resolver.resolve(Samples.album(1), spotifyConfig)
        resolver.resolve(Samples.album(2), spotifyConfig)
        assertEquals(1, net.count("/spotify-accounts/api/token"))

        clock.advance(3541 * 1000L)   // expires_in 3600 s, renewed a minute early
        resolver.resolve(Samples.album(3), spotifyConfig)
        assertEquals(2, net.count("/spotify-accounts/api/token"))
    }

    @Test
    fun spotifyGetsANewTokenOnceAfter401() {
        var tokens = 0
        net.on("/spotify-accounts/api/token", "POST") { tokens++; json("""{"access_token":"tok$tokens","token_type":"Bearer","expires_in":3600}""") }
        net.on("/spotify-api/v1/search") { req ->
            if (req.headers["Authorization"] == "Bearer tok1") status(401, """{"error":{"status":401,"message":"The access token expired"}}""")
            else json(spotifyAlbums("Daft Punk"))
        }

        val r = resolver().resolve(TRACK, spotifyConfig)

        assertEquals("Spotify", r.source)
        assertEquals(2, tokens)
        assertEquals(listOf("Bearer tok1", "Bearer tok2"), net.requests.filter { it.url.encodedPath == "/spotify-api/v1/search" }.map { it.headers["Authorization"] })
    }

    @Test
    fun spotifyGivesUpAfterTheSecond401() {
        net.on("/spotify-accounts/api/token", "POST") { json(spotifyToken) }
        net.on("/spotify-api/v1/search") { status(401) }

        assertTrue(resolver().resolve(TRACK.copy(title = ""), spotifyConfig).isEmpty)
        assertEquals(2, net.count("/spotify-api/v1/search"))
    }

    @Test
    fun spotifyWithoutCredentialsOrWithBadOnes() {
        assertTrue(resolver().resolve(Samples.album(1), spotifyConfig.copy(spotifySecret = "")).isEmpty)
        assertEquals(0, net.requests.size)

        net.on("/spotify-accounts/api/token", "POST") { status(400, """{"error":"invalid_client"}""") }
        assertTrue(resolver().resolve(Samples.album(2), spotifyConfig).isEmpty)
        assertTrue(log.has("Spotify token request failed (HTTP 400)"))
        assertEquals(0, net.count("/spotify-api/v1/search"))
    }

    @Test
    fun spotifyImplausibleArtistIsRejected() {
        net.on("/spotify-accounts/api/token", "POST") { json(spotifyToken) }
        net.on("/spotify-api/v1/search") { req ->
            if (req.url.queryParameter("type") == "album") json(spotifyAlbums("Cover Band Inc"))
            else json("""{"tracks":{"items":[],"total":0}}""")
        }

        assertTrue(resolver().resolve(TRACK, spotifyConfig).isEmpty)
    }

    // ---------------------------------------------------------------- order, preferLocal, limits

    @Test
    fun sourcesAreAskedInTheAimpOrderAndTheFirstHitWins() {
        net.on("/spotify-accounts/api/token", "POST") { json(spotifyToken) }
        net.on("/spotify-api/v1/search") { json("""{"albums":{"items":[]},"tracks":{"items":[]}}""") }
        net.on("/deezer/search/album") { json("""{"data":[]}""") }
        net.on("/deezer/search/track") { json("""{"data":[]}""") }
        net.on("/itunes/search") { json(itunes("Daft Punk")) }
        net.on("/bandcamp/api/bcsearch_public_api/1/autocomplete_elastic", "POST") { json(bandcamp("Daft Punk", "https://f4.bcbits.com/img/a1_3.jpg")) }
        val all = spotifyConfig.copy(srcDeezer = true, srcItunes = true, srcBandcamp = true, srcDiscogs = true,
            discogsToken = "t", srcMusicBrainz = true)

        val r = resolver().resolve(TRACK, all)

        assertEquals("iTunes", r.source)
        val asked = net.requests.map { it.url.encodedPath.split('/')[1] }.distinct()
        assertEquals(listOf("spotify-accounts", "spotify-api", "deezer", "itunes"), asked)
    }

    @Test
    fun preferLocalUploadsFirst() {
        net.onPrefix("/img/") { bytes(JPEG, code = 206) }
        net.on("/x0/", "POST") { text("https://files.test/img/a.jpg") }
        net.on("/deezer/search/album") { json(deezerAlbum("https://cdn-images.dzcdn.net/images/cover/x/1000x1000.jpg")) }

        val r = resolver().resolve(TRACK, UPLOAD_ONLY.copy(srcDeezer = true, preferLocal = true))

        assertEquals("poweramp|x0.at", r.source)
        assertEquals(0, net.count("/deezer/search/album"))
    }

    @Test
    fun onlineFirstWhenLocalIsNotPreferred() {
        net.onPrefix("/img/") { bytes(JPEG, code = 206) }
        net.on("/x0/", "POST") { text("https://files.test/img/a.jpg") }
        net.on("/deezer/search/album") { json(deezerAlbum("https://cdn-images.dzcdn.net/images/cover/x/1000x1000.jpg")) }

        val r = resolver().resolve(TRACK, UPLOAD_ONLY.copy(srcDeezer = true, preferLocal = false))

        assertEquals("Deezer", r.source)
        assertEquals(0, net.count("/x0/"))
    }

    @Test
    fun localCoverIsUploadedWhenNothingIsFoundOnline() {
        net.onPrefix("/img/") { bytes(JPEG, code = 206) }
        net.on("/x0/", "POST") { text("https://files.test/img/a.jpg") }
        net.on("/deezer/search/album") { json("""{"data":[]}""") }
        net.on("/deezer/search/track") { json("""{"data":[]}""") }

        val r = resolver().resolve(TRACK, UPLOAD_ONLY.copy(srcDeezer = true, preferLocal = false))

        assertEquals("poweramp|x0.at", r.source)
        assertEquals(3, net.requests.count { it.url.encodedPath.startsWith("/deezer/") })
    }

    @Test
    fun tooLongUrlsCountAsNotFound() {
        val long = "https://cdn-images.dzcdn.net/images/cover/" + "a".repeat(220) + "/1000x1000.jpg"
        assertTrue(long.length > 256)
        net.on("/deezer/search/album") { json(deezerAlbum(long)) }
        net.on("/deezer/search/track") { json(deezerTrack(long)) }
        net.on("/itunes/search") { json(itunes("Daft Punk")) }

        val resolver = resolver()
        assertEquals("iTunes", resolver.resolve(TRACK, NO_SOURCES.copy(srcDeezer = true, srcItunes = true)).source)
        assertTrue(resolver.resolve(Samples.album(9), NO_SOURCES.copy(srcDeezer = true)).isEmpty)
    }

    @Test
    fun brokenAnswersAreNoHits() {
        net.on("/deezer/search/album") { text("<html>rate limited</html>") }
        net.on("/deezer/search/track") { json("""{"error":{"type":"Exception","message":"Quota limit exceeded","code":4}}""") }
        net.on("/itunes/search") { status(503) }

        assertTrue(resolver().resolve(TRACK, NO_SOURCES.copy(srcDeezer = true, srcItunes = true)).isEmpty)
    }

    // ---------------------------------------------------------------- helpers

    @Test
    fun normAndPlausible() {
        assertEquals("daftpunk", norm("Daft Punk!"))
        assertEquals("bjork", norm("BJÖRK"))
        assertEquals("acdc", norm("AC/DC"))
        assertEquals("letters of every script are kept", "кино", norm("Кино!"))
        assertEquals("坂本龍一", norm("坂本 龍一"))
        // typographic punctuation, accents, fullwidth forms and NBSP compare like the plain spelling
        assertEquals("destinyschild", norm("Destiny’s Child"))
        assertTrue(plausible("Destiny's Child", "Destiny’s Child"))
        assertTrue(plausible("Guns N' Roses", "Guns N’ Roses"))
        assertTrue(plausible("Jay-Z", "Jay–Z"))
        assertTrue(plausible("Beyoncé", "Beyonce"))
        assertTrue(plausible("ＡＢＢＡ", "ABBA"))
        assertTrue(plausible("Sigur Rós", "Sigur Rós"))
        assertFalse(plausible("Кино", "Ария"))
        assertTrue(plausible("Daft Punk", "daft punk"))
        assertTrue(plausible("Daft Punk feat. Romanthony", "Daft Punk"))
        assertTrue(plausible("AC/DC", "ACDC"))
        assertTrue(plausible("anything", ""))
        assertFalse(plausible("", "Daft Punk"))
        assertFalse(plausible("!!!", "Daft Punk"))
        assertFalse(plausible("Justice", "Daft Punk"))
    }

    @Test
    fun credentialsKeepOnlyPrintableAscii() {
        assertEquals("abc123", credential(" ﻿abc​12 3\t\n"))
        assertEquals("tok/en+1_-.~", credential("tok/en+1_-.~"))
        assertEquals("", credential("​ "))
    }

    @Test
    fun urlEncodingKeepsOnlyUnreservedCharacters() {
        assertEquals("Daft%20Punk%20-%20%22Discovery%22", urlEncode("Daft Punk - \"Discovery\""))
        assertEquals("a-b_c.d~e", urlEncode("a-b_c.d~e"))
        assertEquals("Bj%C3%B6rk%3A%2F%2B", urlEncode("Björk:/+"))
    }

    @Test
    fun queryFromTrack() {
        val q = Query.of(TRACK.copy(artist = "  \"Weird\" Al ", title = "Eat It", album = ""))
        assertEquals(Query(artist = "Weird  Al", trackArtist = "Weird  Al", album = "", title = "Eat It"), q)
    }
}
