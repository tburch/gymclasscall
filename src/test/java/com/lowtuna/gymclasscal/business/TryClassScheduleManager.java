package com.lowtuna.gymclasscal.business;

import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.lowtuna.gymclasscal.config.GymClassCalConfig;
import com.lowtuna.gymclasscal.core.ClassInfo;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

@Slf4j
public class TryClassScheduleManager {
    private GymClassCalConfig config = new GymClassCalConfig();

    @Mock(answer = Answers.RETURNS_SMART_NULLS)
    private MetricRegistry metricRegistry;

    @Mock(answer = Answers.RETURNS_SMART_NULLS)
    private Timer timer;

    @Mock(answer = Answers.RETURNS_SMART_NULLS)
    private Timer.Context timerContext;

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

    @Test
    public void testGetMultipleWeeks() {
        ExecutorService executorService = Executors.newCachedThreadPool();
        TwentyFourHourParser parser = new TwentyFourHourParser(config.getClubListBaseUrl(), config.getClubDetailPattern(), config.getClubCalendarTemplate(), metricRegistry, null);
        ClassScheduleManager manager = new ClassScheduleManager(parser, 4, executorService);
        Collection<ClassInfo> allClasses = manager.getClassInfos(572);
        TryClassScheduleManager.log.debug("Found {} classes over 4 weeks", allClasses.size());

    }
}
