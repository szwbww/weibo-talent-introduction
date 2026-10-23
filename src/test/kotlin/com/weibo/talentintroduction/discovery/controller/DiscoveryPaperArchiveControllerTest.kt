package com.weibo.talentintroduction.discovery.controller

import com.weibo.talentintroduction.auth.config.AuthInterceptor
import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.service.AuthService
import com.weibo.talentintroduction.discovery.archive.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.nio.file.Files
import java.time.LocalDateTime

class DiscoveryPaperArchiveControllerTest {
    private lateinit var f: ArchiveFixture
    private lateinit var mvc: MockMvc
    private lateinit var session: MockHttpSession
    private val prefix="/api/expert-discovery/papers"
    @BeforeEach fun setup() {
        ArchiveMysqlFixture.clean(); f=ArchiveFixture(); f.enable()
        val auth=Mockito.mock(AuthService::class.java)
        Mockito.`when`(auth.findUser("fixture")).thenReturn(AdminUser(1,"fixture","unused",false,
            createdAt=LocalDateTime.now(),updatedAt=LocalDateTime.now()))
        session=MockHttpSession(); session.setAttribute(AuthSessionKeys.USERNAME,"fixture")
        mvc=MockMvcBuilders.standaloneSetup(DiscoveryPaperArchiveController(f.service))
            .addInterceptors(AuthInterceptor(auth,f.mapper)).build()
    }
    @AfterEach fun cleanup() { f.close() }
    @Test fun `all new API routes keep authentication`() {
        listOf("/settings","/stats","","/1","/1/attempts/1/content").forEach {
            mvc.perform(get(prefix+it)).andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        }
        listOf("/protection","/delete-preview","/delete").forEach {
            mvc.perform(post(prefix+it).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized)
        }
        mvc.perform(put("$prefix/settings").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized)
    }
    @Test fun `PDF inline range suffix open invalid ranges and attachment`() {
        val record=f.paper("pdf"); val bytes=f.pdf(); val a=f.body(record,bytes)
        val url="$prefix/${record.id}/attempts/${a.id}/content"
        val full=mvc.perform(get(url).session(session)).andExpect(status().isOk)
            .andExpect(header().string("Content-Type","application/pdf"))
            .andExpect(header().string("Cache-Control","private,no-store"))
            .andExpect(header().string("X-Content-Type-Options","nosniff"))
            .andExpect(header().string("Content-Disposition","inline; filename=\"paper-${record.id}-${a.id}.pdf\""))
            .andReturn().response
        assertArrayEquals(bytes,full.contentAsByteArray)
        val range=mvc.perform(get(url).session(session).header("Range","bytes=0-99")).andExpect(status().isPartialContent)
            .andExpect(header().string("Content-Range","bytes 0-99/1024")).andReturn().response
        assertArrayEquals(bytes.copyOfRange(0,100),range.contentAsByteArray)
        assertEquals(10,mvc.perform(get(url).session(session).header("Range","bytes=-10")).andExpect(status().isPartialContent).andReturn().response.contentAsByteArray.size)
        assertEquals(24,mvc.perform(get(url).session(session).header("Range","bytes=1000-")).andExpect(status().isPartialContent).andReturn().response.contentAsByteArray.size)
        listOf("bytes=1024-","bytes=10-1","bytes=0-1,3-4","bytes=-0","bytes=99999999999999999999999-","bytes=-","items=0-99").forEach { rangeValue ->
            mvc.perform(get(url).session(session).header("Range",rangeValue)).andExpect(status().isRequestedRangeNotSatisfiable)
                .andExpect(header().string("Content-Range","bytes */1024"))
        }
        mvc.perform(get(url).session(session).param("download","true")).andExpect(status().isOk)
            .andExpect(header().string("Content-Disposition","attachment; filename=\"paper-${record.id}-${a.id}.pdf\""))
    }
    @Test fun `HTML XML text and falsely labelled PDF never execute as HTML or PDF`() {
        val record=f.paper("html"); val bytes="<script>alert(1)</script>".toByteArray()
        val a=f.body(record,bytes,ArchiveAttemptKind.EMBEDDED_TEXT,"application/pdf")
        val response=mvc.perform(get("$prefix/${record.id}/attempts/${a.id}/content").session(session))
            .andExpect(status().isOk).andExpect(header().string("Content-Type","text/plain;charset=UTF-8"))
            .andExpect(header().string("X-Content-Type-Options","nosniff")).andReturn().response
        assertArrayEquals(bytes,response.contentAsByteArray)
        val xml=f.body(record,"<article>text</article>".toByteArray(),ArchiveAttemptKind.PMC_XML,"application/xml")
        mvc.perform(get("$prefix/${record.id}/attempts/${xml.id}/content").session(session))
            .andExpect(status().isOk).andExpect(header().string("Content-Type","text/plain;charset=UTF-8"))
    }
    @Test fun `positive IDs cross record ownership missing deleted and symlinks`() {
        val a=f.paper("A"); val b=f.paper("B"); val attempt=f.body(a,f.pdf())
        mvc.perform(get("$prefix/${b.id}/attempts/${attempt.id}/content").session(session)).andExpect(status().isNotFound)
        listOf("0","-1","1.5","999999999999999999999999").forEach {
            mvc.perform(get("$prefix/$it").session(session)).andExpect(status().isBadRequest)
        }
        mvc.perform(get("$prefix/999999").session(session)).andExpect(status().isNotFound)
        val preview=f.service.deletePreview(listOf(a.id)); f.service.delete(ArchiveDeleteRequest(preview.ids,preview.snapshotHash))
        mvc.perform(get("$prefix/${a.id}/attempts/${attempt.id}/content").session(session)).andExpect(status().isGone)
        val missing=f.body(b,f.pdf(1024,66))
        val path=f.root.resolve("blobs/${missing.blobSha256!!.take(2)}/${missing.blobSha256}.bin")
        Files.delete(path)
        mvc.perform(get("$prefix/${b.id}/attempts/${missing.id}/content").session(session)).andExpect(status().isGone)
            .andExpect(jsonPath("$.code").value("SOURCE_MISSING"))
        val symlink=f.body(b,f.pdf(1024,67))
        val link=f.root.resolve("blobs/${symlink.blobSha256!!.take(2)}/${symlink.blobSha256}.bin")
        Files.delete(link); Files.createSymbolicLink(link,f.directory.resolve("outside"))
        val unsafe=mvc.perform(get("$prefix/${b.id}/attempts/${symlink.id}/content").session(session)).andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.code").value("UNSAFE_PATH")).andReturn().response.contentAsString
        assertFalse(unsafe.contains(f.directory.toString()))
    }
    @Test fun `settings strict integral revision CAS and storage DTO shapes`() {
        mvc.perform(get("$prefix/settings").session(session)).andExpect(status().isOk)
            .andExpect(jsonPath("$.revision").value(1)).andExpect(jsonPath("$.effectiveEnabled").value(true))
        val update="""{"revision":1,"enabled":true,"maxBytes":26843545600,"minFreeBytes":16106127360}"""
        mvc.perform(put("$prefix/settings").session(session).contentType(MediaType.APPLICATION_JSON).content(update))
            .andExpect(status().isOk).andExpect(jsonPath("$.revision").value(2)).andExpect(jsonPath("$.maxBytes").value(26843545600L))
        mvc.perform(put("$prefix/settings").session(session).contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isConflict)
        mvc.perform(put("$prefix/settings").session(session).contentType(MediaType.APPLICATION_JSON).content(update.replace("\"revision\":1","\"revision\":2.5"))).andExpect(status().isBadRequest)
        mvc.perform(get("$prefix/stats").session(session)).andExpect(status().isOk)
            .andExpect(jsonPath("$.storage.measuredAt").exists()).andExpect(jsonPath("$.storage.archiveUsedBytes").value(0))
            .andExpect(jsonPath("$.counts.savedAttempts").value(0)).andExpect(jsonPath("$.counts.reusedAttempts").value(0))
    }
    @Test fun `list detail protection deletion protocol and no local paths URL secrets`() {
        val r=f.paper("protocol"); f.body(r,f.pdf())
        val detail=mvc.perform(get("$prefix/${r.id}").session(session)).andExpect(status().isOk)
            .andExpect(jsonPath("$.record.state").value("STORED")).andExpect(jsonPath("$.record.analysisSummary").isEmpty)
            .andExpect(jsonPath("$.attempts[0].readable").value(true)).andExpect(jsonPath("$.attempts[0].sourceUrl").value("https://fixture.invalid/paper"))
            .andReturn().response.contentAsString
        assertFalse(detail.contains(f.directory.toString())); assertFalse(detail.contains("secret"))
        mvc.perform(get(prefix).session(session).param("executionId","1").param("status","STORED").param("size","20"))
            .andExpect(status().isOk).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].attemptCount").value(1))
        val revision=f.service.detail(r.id).record.revision
        val protect="""{"items":[{"id":${r.id},"revision":$revision}],"protected":true}"""
        mvc.perform(post("$prefix/protection").session(session).contentType(MediaType.APPLICATION_JSON).content(protect))
            .andExpect(status().isOk).andExpect(jsonPath("$.changed").value(1))
        val ids="""{"ids":[${r.id}]}"""
        val preview=mvc.perform(post("$prefix/delete-preview").session(session).contentType(MediaType.APPLICATION_JSON).content(ids))
            .andExpect(status().isOk).andExpect(jsonPath("$.deletableFiles").value(0)).andReturn().response.contentAsString
        val hash=f.mapper.readTree(preview).path("snapshotHash").asText()
        f.protect(r.id,false)
        mvc.perform(post("$prefix/delete").session(session).contentType(MediaType.APPLICATION_JSON)
            .content("""{"ids":[${r.id}],"snapshotHash":"$hash"}"""))
            .andExpect(status().isConflict).andExpect(jsonPath("$.preview.deletableFiles").value(1))
        val fresh=f.service.deletePreview(listOf(r.id))
        mvc.perform(post("$prefix/delete").session(session).contentType(MediaType.APPLICATION_JSON)
            .content("""{"ids":[${r.id}],"snapshotHash":"${fresh.snapshotHash}"}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.deletedFiles").value(1)).andExpect(jsonPath("$.deletedBytes").value(1024))
    }
    @Test fun `invalid batches queries integer paths and fractional IDs are rejected`() {
        listOf("{\"ids\":[]}","{\"ids\":[0]}","{\"ids\":[1,1]}","{\"ids\":[1.9]}","{\"ids\":[9223372036854775808]}","{\"ids\":[\"../etc/passwd\"]}",
            "{\"ids\":["+(1..201).joinToString(",")+"]}").forEach { body ->
            mvc.perform(post("$prefix/delete-preview").session(session).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest)
        }
        mvc.perform(get(prefix).session(session).param("q","x".repeat(201))).andExpect(status().isBadRequest)
        mvc.perform(get(prefix).session(session).param("size","1")).andExpect(status().isBadRequest)
        mvc.perform(get(prefix).session(session).param("executionId","-1")).andExpect(status().isBadRequest)
        mvc.perform(get(prefix).session(session).param("page","-1")).andExpect(status().isBadRequest)
    }
}
