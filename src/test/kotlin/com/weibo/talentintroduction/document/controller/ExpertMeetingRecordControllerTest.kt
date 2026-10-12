package com.weibo.talentintroduction.document.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.document.service.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class ExpertMeetingRecordControllerTest {
    private val service = Mockito.mock(ExpertMeetingRecordService::class.java)
    private val mvc = MockMvcBuilders.standaloneSetup(ExpertMeetingRecordController(service))
        .setMessageConverters(MappingJackson2HttpMessageConverter(jacksonObjectMapper())).build()
    private fun session() = MockHttpSession().apply { setAttribute(AuthSessionKeys.USERNAME,"admin") }
    @Test fun `both endpoints require authenticated session`() {
        mvc.perform(get("/api/experts/meeting-records").param("expertId","expert-a")).andExpect(status().isUnauthorized)
        mvc.perform(put("/api/experts/meeting-records/id").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized)
        Mockito.verifyNoInteractions(service)
    }
    @Test fun `bad json and pagination return 400 without service calls`() {
        mvc.perform(get("/api/experts/meeting-records").session(session()).param("expertId","expert-a").param("page","oops"))
            .andExpect(status().isBadRequest)
        mvc.perform(put("/api/experts/meeting-records/id").session(session()).contentType(MediaType.APPLICATION_JSON).content("{broken"))
            .andExpect(status().isBadRequest)
        Mockito.verifyNoInteractions(service)
    }
    @Test fun `read preserves expert and pagination scope`() {
        Mockito.`when`(service.list("expert-a",1,20)).thenReturn(MeetingRecordPage(emptyList(),0,1,20))
        mvc.perform(get("/api/experts/meeting-records").session(session()).param("expertId","expert-a").param("page","1"))
            .andExpect(status().isOk).andExpect(jsonPath("$.page").value(1))
        Mockito.verify(service).list("expert-a",1,20)
    }
    @Test fun `save derives actor from session and keeps version conflict`() {
        val input=MeetingRecordInput(expertId="expert-a",title="会议",expectedVersion=2)
        Mockito.`when`(service.save("id",input,"admin")).thenThrow(MeetingRecordException(org.springframework.http.HttpStatus.CONFLICT,"请刷新"))
        mvc.perform(put("/api/experts/meeting-records/id").session(session()).contentType(MediaType.APPLICATION_JSON)
            .content(jacksonObjectMapper().writeValueAsString(input))).andExpect(status().isConflict)
        Mockito.verify(service).save("id",input,"admin")
    }
}
