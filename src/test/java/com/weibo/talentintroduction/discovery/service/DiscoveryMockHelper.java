package com.weibo.talentintroduction.discovery.service;

import com.weibo.talentintroduction.discovery.domain.AuthorEmail;
import com.weibo.talentintroduction.discovery.domain.EmailExtractionOutcome;
import com.weibo.talentintroduction.discovery.domain.PaperMetadata;
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria;
import com.weibo.talentintroduction.discovery.domain.PaperSearchResult;
import com.weibo.talentintroduction.expert.domain.EligibilityResult;
import com.weibo.talentintroduction.expert.domain.EmailValidationResult;
import com.weibo.talentintroduction.expert.domain.ExpertProfile;
import com.weibo.talentintroduction.expert.service.CandidateEligibilityService;
import com.weibo.talentintroduction.expert.service.EmailValidationService;
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService;
import com.weibo.talentintroduction.task.service.TaskProgress;
import com.weibo.talentintroduction.task.service.TaskProgressStore;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

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

    public static void stubExtractAuthorEmails(AcademicDataSource mock, List<AuthorEmail> emails) {
        Mockito.when(mock.extractAuthorEmails(Mockito.any(PaperMetadata.class)))
            .thenReturn(new EmailExtractionOutcome(emails, "FULLTEXT_XML", null));
    }

    public static void stubExtractAuthorEmailsEmpty(AcademicDataSource mock, String failureReason) {
        Mockito.when(mock.extractAuthorEmails(Mockito.any(PaperMetadata.class)))
            .thenReturn(new EmailExtractionOutcome(Collections.emptyList(), "FULLTEXT_XML", failureReason));
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
        Mockito.doThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND))
            .when(mock).exchange(
                Mockito.contains("/_doc/"), Mockito.eq(HttpMethod.HEAD), Mockito.any(),
                Mockito.eq(Void.class)
            );
    }

    public static void stubEsDedupSearchError(RestTemplate mock) {
        Mockito.doThrow(new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
            .when(mock).exchange(
                Mockito.contains("/_search"), Mockito.eq(HttpMethod.POST), Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode.class)
            );
    }

    public static void stubSourceInfo(EuropePmcDataSource mock) {
        Mockito.doReturn("EUROPE_PMC").when(mock).getSourceName();
        Mockito.doReturn("FULLTEXT_XML").when(mock).getEmailExtractionMethod();
        Mockito.doReturn(500).when(mock).getMaxPapersPerSource();
    }

    public static void stubMaxPapersPerSource(AcademicDataSource mock, int value) {
        Mockito.doReturn(value).when(mock).getMaxPapersPerSource();
    }

    public static void stubExtractAuthorEmailsOutcome(AcademicDataSource mock, EmailExtractionOutcome outcome) {
        Mockito.when(mock.extractAuthorEmails(Mockito.any(PaperMetadata.class))).thenReturn(outcome);
    }

    public static void stubSearchPapersThrows(AcademicDataSource mock, Exception exception) {
        Mockito.doThrow(exception).when(mock).searchPapers(Mockito.any(PaperSearchCriteria.class));
    }

    public static void stubOrcidSourceName(OrcidDataSource mock) {
        Mockito.doReturn("ORCID").when(mock).getSourceName();
    }

    public static void stubOrcidMaxRecordsPerRun(OrcidDataSource mock, int value) {
        Mockito.doReturn(value).when(mock).getMaxRecordsPerRun();
    }

    public static void stubOrcidSearchRecords(OrcidDataSource mock, java.util.List<OrcidDataSource.OrcidRecord> records) {
        Mockito.doReturn(records)
            .doReturn(java.util.Collections.emptyList())
            .when(mock).searchOrcidRecords(Mockito.any(PaperSearchCriteria.class));
    }

    public static void stubOrcidRecordToAuthorEmails(OrcidDataSource mock) {
        Mockito.doAnswer(invocation -> {
            OrcidDataSource.OrcidRecord record = invocation.getArgument(0);
            return record.getEmails().stream()
                .map(email -> new AuthorEmail(email, record.getGivenNames(), record.getFamilyNames(),
                    false, record.getInstitutionName(), record.getOrcidId()))
                .collect(java.util.stream.Collectors.toList());
        }).when(mock).orcidRecordToAuthorEmails(Mockito.any(OrcidDataSource.OrcidRecord.class));
    }

    /**
     * Creates an answer that captures all TaskProgress objects into the given list.
     * Usage: helper.captureProgressUpdates(progressStore, capturedList)
     */
    @SuppressWarnings("unchecked")
    public static void captureProgressUpdates(TaskProgressStore mock, List<TaskProgress> captured) {
        Mockito.doAnswer(invocation -> {
            TaskProgress progress = invocation.getArgument(1);
            captured.add(progress);
            return null;
        }).when(mock).update(
            Mockito.anyString(),
            Mockito.any(TaskProgress.class),
            Mockito.any()
        );
    }

    public static void stubEsCandidateHeadExists(RestTemplate mock) {
        Mockito.doReturn(ResponseEntity.ok().<Void>build())
            .when(mock).exchange(
                Mockito.contains("orcid_info_candidate/_doc/"),
                Mockito.eq(HttpMethod.HEAD),
                Mockito.any(),
                Mockito.eq(Void.class)
            );
    }

    public static void stubEsRawDocGet(RestTemplate mock) {
        var mapper = new ObjectMapper();
        var source = mapper.createObjectNode()
            .put("orcidId", "0000-0001-raw")
            .put("displayName", "Test User");
        var body = mapper.createObjectNode();
        body.set("_source", source);
        Mockito.doReturn(ResponseEntity.ok((com.fasterxml.jackson.databind.JsonNode) body))
            .when(mock).exchange(
                Mockito.contains("orcid_info/_doc/"),
                Mockito.eq(HttpMethod.GET),
                Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode.class)
            );
    }

    public static void stubEsHeadServerError(RestTemplate mock) {
        Mockito.doThrow(new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
            .when(mock).exchange(
                Mockito.contains("orcid_info_candidate/_doc/"),
                Mockito.eq(HttpMethod.HEAD),
                Mockito.any(),
                Mockito.eq(Void.class)
            );
    }

    public static void stubEsRawDocGetCustom(RestTemplate mock, String orcidId, String displayName) {
        var mapper = new ObjectMapper();
        var source = mapper.createObjectNode()
            .put("orcidId", orcidId)
            .put("displayName", displayName);
        var body = mapper.createObjectNode();
        body.set("_source", source);
        Mockito.doReturn(ResponseEntity.ok((com.fasterxml.jackson.databind.JsonNode) body))
            .when(mock).exchange(
                Mockito.contains("orcid_info/_doc/" + orcidId),
                Mockito.eq(HttpMethod.GET),
                Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode.class)
            );
    }

    public static void verifyCandidatePutCalled(RestTemplate mock, int times) {
        Mockito.verify(mock, Mockito.times(times)).exchange(
            Mockito.contains("orcid_info_candidate/_doc/"),
            Mockito.eq(HttpMethod.PUT),
            Mockito.any(),
            Mockito.eq(com.fasterxml.jackson.databind.JsonNode.class)
        );
    }

    public static void verifyCandidatePutNeverCalled(RestTemplate mock) {
        Mockito.verify(mock, Mockito.never()).exchange(
            Mockito.contains("orcid_info_candidate/_doc/"),
            Mockito.eq(HttpMethod.PUT),
            Mockito.any(),
            Mockito.eq(com.fasterxml.jackson.databind.JsonNode.class)
        );
    }

    public static void verifyRawUpdateCalled(RestTemplate mock, int times) {
        Mockito.verify(mock, Mockito.times(times)).exchange(
            Mockito.contains("orcid_info/_update/"),
            Mockito.eq(HttpMethod.POST),
            Mockito.any(),
            Mockito.eq(com.fasterxml.jackson.databind.JsonNode.class)
        );
    }

    public static void verifyOrcidSearchRecordsCalled(OrcidDataSource mock, int times) {
        Mockito.verify(mock, Mockito.times(times)).searchOrcidRecords(Mockito.any());
    }

    /**
     * Stub isCancelled to return false for the first {@code falseCount} calls, then true.
     * Use {@code 2} for "cancel after ORCID returns" scenarios, {@code 4} for "cancel after RAW update".
     */
    public static void stubCancelledAfterNCalls(TaskProgressStore mock, int falseCount) {
        var counter = new java.util.concurrent.atomic.AtomicInteger(0);
        Mockito.doAnswer(inv -> {
            int call = counter.incrementAndGet();
            return call > falseCount;
        }).when(mock).isCancelled(
            Mockito.eq("EXPERT_DISCOVERY")
        );
    }
}
