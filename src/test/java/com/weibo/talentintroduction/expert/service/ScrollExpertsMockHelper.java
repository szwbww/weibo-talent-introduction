package com.weibo.talentintroduction.expert.service;

import com.weibo.talentintroduction.expert.domain.ExpertProfile;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import java.util.List;
import java.util.Map;

/**
 * Java helper to stub service methods without hitting Kotlin null-check issues.
 * Mockito's any() returns null which triggers Kotlin's non-null parameter checks
 * before Mockito can intercept. Java has no such checks, so calling from Java works.
 */
@SuppressWarnings("unchecked")
public class ScrollExpertsMockHelper {

    @SuppressWarnings("rawtypes")
    public static void stubScrollExperts(
        ExpertSearchService mock,
        List<List<ExpertProfile>> batches
    ) {
        var totalHits = batches.stream().mapToLong(List::size).sum();
        stubScrollExperts(mock, batches, totalHits);
    }

    @SuppressWarnings("rawtypes")
    public static void stubScrollExperts(
        ExpertSearchService mock,
        List<List<ExpertProfile>> batches,
        long totalHits
    ) {
        Mockito.doAnswer((Answer<Void>) invocation -> {
            var handler = (kotlin.jvm.functions.Function3<List<ExpertProfile>, Integer, Long, Boolean>) invocation.getArguments()[2];
            var batchNumber = 0;
            for (var batch : batches) {
                batchNumber++;
                var shouldContinue = handler.invoke(batch, batchNumber, totalHits);
                if (!shouldContinue) break;
            }
            return null;
        }).when(mock).scrollExperts(
            Mockito.any(com.weibo.talentintroduction.expert.domain.ExpertIndexLevel.class),
            Mockito.anyInt(),
            Mockito.any(kotlin.jvm.functions.Function3.class)
        );
    }

    @SuppressWarnings("rawtypes")
    public static void stubScrollExpertsFiltered(
        ExpertSearchService mock,
        List<List<ExpertProfile>> batches
    ) {
        Mockito.doAnswer((Answer<Void>) invocation -> {
            var args = invocation.getArguments();
            var handler = (kotlin.jvm.functions.Function1<List<ExpertProfile>, Boolean>) args[args.length - 1];
            for (var batch : batches) {
                var shouldContinue = handler.invoke(batch);
                if (!shouldContinue) break;
            }
            return null;
        }).when(mock).scrollExpertsFiltered(
            Mockito.any(com.weibo.talentintroduction.expert.domain.ExpertIndexLevel.class),
            Mockito.anyList(),
            Mockito.anyInt(),
            Mockito.any(kotlin.jvm.functions.Function1.class)
        );
    }

    public static void stubCountExperts(
        com.weibo.talentintroduction.expert.service.ExpertSearchService mock,
        long total,
        long pending
    ) {
        Mockito.doAnswer(invocation -> {
            var filters = (java.util.List<?>) invocation.getArgument(1);
            return filters.isEmpty() ? total : pending;
        }).when(mock).countExperts(
            Mockito.eq(com.weibo.talentintroduction.expert.domain.ExpertIndexLevel.CANDIDATE),
            Mockito.anyList()
        );
    }

    public static void stubWriteCandidateDocument(ExpertIndexWriterService mock, boolean success) {
        Mockito.when(mock.writeCandidateDocument(Mockito.anyString(), Mockito.anyMap())).thenReturn(success);
    }

    public static void verifyWriteCandidateDocumentWithDocId(ExpertIndexWriterService mock, String docId) {
        Mockito.verify(mock).writeCandidateDocument(Mockito.eq(docId), Mockito.anyMap());
    }

    public static void verifyWriteCandidateDocumentWithDocIdAndOrcid(ExpertIndexWriterService mock, String docId, String orcidId) {
        Mockito.verify(mock).writeCandidateDocument(Mockito.eq(docId), Mockito.argThat(doc -> orcidId.equals(doc.get("orcidId"))));
    }

    public static void verifyNeverWriteCandidateDocumentWithDocId(ExpertIndexWriterService mock, String docId) {
        Mockito.verify(mock, Mockito.never()).writeCandidateDocument(Mockito.eq(docId), Mockito.anyMap());
    }

    public static void stubReadRawDocument(ExpertIndexWriterService mock, Map<String, Object> doc) {
        Mockito.when(mock.readRawDocument(Mockito.anyString())).thenReturn(doc);
    }

    public static void stubDocumentExistsInIndex(ExpertIndexWriterService mock, boolean exists) {
        Mockito.when(mock.documentExistsInIndex(
            Mockito.any(com.weibo.talentintroduction.expert.domain.ExpertIndexLevel.class),
            Mockito.anyString()
        )).thenReturn(exists);
    }
}
