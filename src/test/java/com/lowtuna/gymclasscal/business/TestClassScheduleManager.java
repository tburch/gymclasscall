package com.lowtuna.gymclasscal.business;

import java.io.InputStream;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.base.Optional;
import com.lowtuna.gymclasscal.config.GymClassCalConfig;
import com.lowtuna.gymclasscal.core.ClassInfo;
import com.lowtuna.gymclasscal.util.JsoupDocumentLoader;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

@Slf4j
public class TestClassScheduleManager {
    private GymClassCalConfig config = new GymClassCalConfig();

    @Mock(answer = Answers.RETURNS_SMART_NULLS)
    private MetricRegistry metricRegistry;

    @Mock(answer = Answers.RETURNS_SMART_NULLS)
    private Timer timer;

    @Mock(answer = Answers.RETURNS_SMART_NULLS)
    private Timer.Context timerContext;

    @Mock(answer = Answers.RETURNS_SMART_NULLS)
    private JsoupDocumentLoader documentLoader;

    @Before
    public void initMockAnnotations() {
        MockitoAnnotations.initMocks(this);
    }

    @Before
    public void initMetrics() {
        Mockito.when(metricRegistry.timer(Mockito.anyString())).then(new Answer<Timer>() {
            @Override
            public Timer answer(InvocationOnMock invocation) throws Throwable {
                return timer;
            }
        });

        Mockito.when(timer.time()).then(new Answer<Timer.Context>() {
            @Override
            public Timer.Context answer(InvocationOnMock invocation) throws Throwable {
                return timerContext;
            }
        });
    }

    private AtomicInteger documentRequestCount = new AtomicInteger(0);

    @Before
    public void initDocumentLoader() {
        when(documentLoader.loadDocument(anyString())).then(new Answer<Optional<Document>>() {
            @Override
            public Optional<Document> answer(InvocationOnMock invocation) throws Throwable {
                InputStream is = null;
                switch (documentRequestCount.incrementAndGet()) {
                    case 1:
                        is = getClass().getClassLoader().getResourceAsStream("572_2014-01-27.html");
                        break;
                    case 2:
                        is = getClass().getClassLoader().getResourceAsStream("572_2014-02-03.html");
                        break;
                    case 3:
                        is = getClass().getClassLoader().getResourceAsStream("572_2014-02-10.html");
                        break;
                    case 4:
                        is = getClass().getClassLoader().getResourceAsStream("572_2014-02-17.html");
                        break;
                }
                Document document = Jsoup.parse(is, "utf-8", "http://24hourfit.schedulesource.com/public/");
                return Optional.fromNullable(document);
            }
        });
    }

    @Test
    public void testGetMultipleWeeks() {
        ExecutorService executorService = Executors.newCachedThreadPool();
        TwentyFourHourParser parser = new TwentyFourHourParser(config.getClubListBaseUrl(), config.getClubDetailPattern(), config.getClubCalendarTemplate(), metricRegistry, null, documentLoader);
        ClassScheduleManager manager = new ClassScheduleManager(parser, 4, executorService);
        Collection<ClassInfo> allClasses = manager.getClassInfos(572);
        TestClassScheduleManager.log.debug("Found {} classes over 4 weeks", allClasses.size());
    }
}
