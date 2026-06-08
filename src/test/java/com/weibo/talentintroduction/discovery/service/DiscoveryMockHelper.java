package com.weibo.talentintroduction.discovery.service;

import com.weibo.talentintroduction.discovery.domain.AuthorEmail;
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria;
import com.weibo.talentintroduction.discovery.domain.PaperSearchResult;
import com.weibo.talentintroduction.expert.domain.EligibilityResult;
import com.weibo.talentintroduction.expert.domain.EmailValidationResult;
import com.weibo.talentintroduction.expert.domain.ExpertProfile;
import com.weibo.talentintroduction.expert.service.CandidateEligibilityService;
import com.weibo.talentintroduction.expert.service.EmailValidationService;
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Java helper to stub discovery service dependencies without hitting Kotlin null-check issues.
 */
@SuppressWarnings("unchecked")
public class DiscoveryMockHelper {

    public static void stubSearchPapers(EuropePmcDataSource mock, PaperSearchResult result) {
        Mockito.when(mock.searchPapers(
            Mockito.any(PaperSearchCriteria.class)
        )).thenReturn(result);
    }

    public static void stubExtractEmails(EuropePmcDataSource mock, String pmcId, List<AuthorEmail> emails) {
        Mockito.when(mock.extractEmailsFromFullText(pmcId)).thenReturn(emails);
    }

    public static void stubValidateEmail(EmailValidationService mock, String email, EmailValidationResult result) {
        Mockito.when(mock.validate(email)).thenReturn(result);
    }

    public static void stubEligibilityTrue(CandidateEligibilityService mock) {
        Mockito.when(mock.evaluateEligibility(
            Mockito.any(ExpertProfile.class)
        )).thenReturn(new EligibilityResult(true, Collections.emptyList()));
    }

    public static void stubEligibilityFalse(CandidateEligibilityService mock, List<String> reasons) {
        Mockito.when(mock.evaluateEligibility(
            Mockito.any(ExpertProfile.class)
        )).thenReturn(new EligibilityResult(false, reasons));
    }

    public static void stubIndexToRaw(ExpertIndexWriterService mock, boolean success) {
        Mockito.when(mock.indexToRaw(Mockito.anyString(), Mockito.anyMap())).thenReturn(success);
    }

    public static void stubEsDedupSearch(RestTemplate mock, int totalHits) {
        ObjectNode body = new ObjectMapper().createObjectNode();
        body.putObject("hits").putObject("total").put("value", totalHits);
        Mockito.when(mock.exchange(
            Mockito.contains("/_search"), Mockito.eq(HttpMethod.POST), Mockito.any(),
            Mockito.eq(com.fasterxml.jackson.databind.JsonNode.class)
        )).thenReturn(ResponseEntity.ok((com.fasterxml.jackson.databind.JsonNode) body));
    }

    public static void stubEsRawUpdate(RestTemplate mock) {
        Mockito.when(mock.exchange(
            Mockito.contains("/_update"), Mockito.eq(HttpMethod.POST), Mockito.any(),
            Mockito.eq(com.fasterxml.jackson.databind.JsonNode.class)
        )).thenReturn(ResponseEntity.ok(new ObjectMapper().createObjectNode()));
    }

    public static void stubEsCandidatePut(RestTemplate mock, boolean success) {
        if (success) {
            Mockito.when(mock.exchange(
                Mockito.contains("orcid_info_candidate/_doc/"), Mockito.eq(HttpMethod.PUT), Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode.class)
            )).thenReturn(ResponseEntity.ok(new ObjectMapper().createObjectNode()));
        } else {
            Mockito.when(mock.exchange(
                Mockito.contains("orcid_info_candidate/_doc/"), Mockito.eq(HttpMethod.PUT), Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode.class)
            )).thenThrow(new RuntimeException("ES write failed"));
        }
    }

    public static void stubEsHeadNotFound(RestTemplate mock) {
        Mockito.when(mock.exchange(
            Mockito.contains("/_doc/"), Mockito.eq(HttpMethod.HEAD), Mockito.any(),
            Mockito.eq(Void.class)
        )).thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));
    }

    public static void stubEsDedupSearchError(RestTemplate mock) {
        Mockito.when(mock.exchange(
            Mockito.contains("/_search"), Mockito.eq(HttpMethod.POST), Mockito.any(),
            Mockito.eq(com.fasterxml.jackson.databind.JsonNode.class)
        )).thenThrow(new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR));
    }
}
