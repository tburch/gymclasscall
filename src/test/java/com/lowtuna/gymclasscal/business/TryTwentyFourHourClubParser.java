package com.lowtuna.gymclasscal.business;

import java.util.Set;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.collect.Sets;
import com.lowtuna.gymclasscal.config.GymClassCalConfig;
import com.lowtuna.gymclasscal.core.ClassInfo;
import com.lowtuna.gymclasscal.core.Club;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

@Slf4j
public class TryTwentyFourHourClubParser {
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
    public void tryFetchClubDetails() {
        TwentyFourHourParser parser = new TwentyFourHourParser(config.getClubListBaseUrl(), config.getClubDetailPattern(), config.getClubCalendarTemplate(), metricRegistry, null);
        Club club = parser.fetchClubInfo(572);
        TryTwentyFourHourClubParser.log.debug("Club details were {}", club);
    }

    @Test
    public void tryFetchClubIds() {
        TwentyFourHourParser parser = new TwentyFourHourParser(config.getClubListBaseUrl(), config.getClubDetailPattern(), config.getClubCalendarTemplate(), metricRegistry, null);
        Set<Integer> clubIds = parser.getClubIds();
        TryTwentyFourHourClubParser.log.debug("Club ids were {}", clubIds);
        TryTwentyFourHourClubParser.log.debug("There were {} total clubs", clubIds.size());
    }

    @Test
    public void tryFetchClassSchedules() {
        LocalDate weekStart = (new LocalDate()).dayOfWeek().withMinimumValue();
        TwentyFourHourParser parser = new TwentyFourHourParser(config.getClubListBaseUrl(), config.getClubDetailPattern(), config.getClubCalendarTemplate(), metricRegistry, null);
        Set<ClassInfo> schedules = parser.fetchClassSchedules(572, weekStart);
        TryTwentyFourHourClubParser.log.debug("Schedules were {}", schedules);
        TryTwentyFourHourClubParser.log.debug("There were {} total schedules", schedules.size());
    }

    @Test
    public void tryFetchAllClassSchedules() {
        LocalDate weekStart = (new LocalDate()).dayOfWeek().withMinimumValue();
        TwentyFourHourParser parser = new TwentyFourHourParser(config.getClubListBaseUrl(), config.getClubDetailPattern(), config.getClubCalendarTemplate(), metricRegistry, null);
        Set<ClassInfo> schedules = Sets.newHashSet();
        Set<Integer> clubIds = parser.getClubIds();
        for (Integer clubId: clubIds) {
            Set<ClassInfo> clubSchedules = parser.fetchClassSchedules(clubId, weekStart);
            schedules.addAll(clubSchedules);
        }
        TryTwentyFourHourClubParser.log.debug("There were {} total schedules", schedules.size());
    }

}
