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

    public static void stubScrollExperts(
        ExpertSearchService mock,
        List<List<ExpertProfile>> batches
    ) {
        Mockito.doAnswer((Answer<Void>) invocation -> {
            var handler = (kotlin.jvm.functions.Function1<List<ExpertProfile>, Boolean>) invocation.getArguments()[2];
            for (var batch : batches) {
                var shouldContinue = handler.invoke(batch);
                if (!shouldContinue) break;
            }
            return null;
        }).when(mock).scrollExperts(
            Mockito.any(com.weibo.talentintroduction.expert.domain.ExpertIndexLevel.class),
            Mockito.anyInt(),
            Mockito.any()
        );
    }

    public static void stubWriteCandidateDocument(ExpertIndexWriterService mock, boolean success) {
        Mockito.when(mock.writeCandidateDocument(Mockito.anyString(), Mockito.anyMap())).thenReturn(success);
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
