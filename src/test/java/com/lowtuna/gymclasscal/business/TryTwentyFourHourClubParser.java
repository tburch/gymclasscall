package com.lowtuna.gymclasscal.business;

import java.io.InputStream;
import java.util.Set;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.base.Optional;
import com.google.common.collect.Sets;
import com.lowtuna.gymclasscal.config.GymClassCalConfig;
import com.lowtuna.gymclasscal.core.ClassInfo;
import com.lowtuna.gymclasscal.core.Club;
import com.lowtuna.gymclasscal.util.JsoupDocumentLoader;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.LocalDate;
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

import static org.mockito.Mockito.*;

@Slf4j
public class TryTwentyFourHourClubParser {
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
        when(metricRegistry.timer(anyString())).then(new Answer<Timer>() {
            @Override
            public Timer answer(InvocationOnMock invocation) throws Throwable {
                return timer;
            }
        });

        when(timer.time()).then(new Answer<Timer.Context>() {
            @Override
            public Timer.Context answer(InvocationOnMock invocation) throws Throwable {
                return timerContext;
            }
        });
    }

    @Before
    public void initDocumentLoader() {
        when(documentLoader.loadDocument(anyString())).then(new Answer<Optional<Document>>() {
            @Override
            public Optional<Document> answer(InvocationOnMock invocation) throws Throwable {
                InputStream is = getClass().getClassLoader().getResourceAsStream("572_2014-01-27.html");
                Document document = Jsoup.parse(is, "utf-8", "http://24hourfit.schedulesource.com/public/");
                return Optional.fromNullable(document);
            }
        });
    }

    @Test
    public void tryFetchClubDetails() {
        TwentyFourHourParser parser = new TwentyFourHourParser(config.getClubListBaseUrl(), config.getClubDetailPattern(), config.getClubCalendarTemplate(), metricRegistry, null, documentLoader);
        Club club = parser.fetchClubInfo(572);
        TryTwentyFourHourClubParser.log.debug("Club details were {}", club);
    }

    @Test
    public void tryFetchClubIds() {
        TwentyFourHourParser parser = new TwentyFourHourParser(config.getClubListBaseUrl(), config.getClubDetailPattern(), config.getClubCalendarTemplate(), metricRegistry, null, documentLoader);
        Set<Integer> clubIds = parser.getClubIds();
        TryTwentyFourHourClubParser.log.debug("Club ids were {}", clubIds);
        TryTwentyFourHourClubParser.log.debug("There were {} total clubs", clubIds.size());
    }

    @Test
    public void tryFetchClassSchedules() {
        LocalDate weekStart = (new LocalDate()).dayOfWeek().withMinimumValue();
        TwentyFourHourParser parser = new TwentyFourHourParser(config.getClubListBaseUrl(), config.getClubDetailPattern(), config.getClubCalendarTemplate(), metricRegistry, null, documentLoader);
        Set<ClassInfo> schedules = parser.fetchClassSchedules(572, weekStart);
        TryTwentyFourHourClubParser.log.debug("Schedules were {}", schedules);
        TryTwentyFourHourClubParser.log.debug("There were {} total schedules", schedules.size());
    }

    @Test
    public void tryFetchAllClassSchedules() {
        LocalDate weekStart = (new LocalDate()).dayOfWeek().withMinimumValue();
        TwentyFourHourParser parser = new TwentyFourHourParser(config.getClubListBaseUrl(), config.getClubDetailPattern(), config.getClubCalendarTemplate(), metricRegistry, null, documentLoader);
        Set<ClassInfo> schedules = Sets.newHashSet();
        Set<Integer> clubIds = parser.getClubIds();
        for (Integer clubId: clubIds) {
            Set<ClassInfo> clubSchedules = parser.fetchClassSchedules(clubId, weekStart);
            schedules.addAll(clubSchedules);
        }
        TryTwentyFourHourClubParser.log.debug("There were {} total schedules", schedules.size());
    }

}
