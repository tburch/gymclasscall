package com.lowtuna.gymclasscal.parser.twentyfourhour;

import java.util.Set;

import com.google.common.collect.Sets;
import com.lowtuna.gymclasscal.business.TwentyFourHourParser;
import com.lowtuna.gymclasscal.config.GymClassCalConfig;
import com.lowtuna.gymclasscal.core.ClassInfo;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.LocalDate;
import org.junit.Test;

@Slf4j
public class TryTwentyFourHourClubParser {
    private GymClassCalConfig config = new GymClassCalConfig();

    @Test
    public void tryFetchClubIds() {
        TwentyFourHourParser parser = new TwentyFourHourParser(config.getClubListBaseUrl(), config.getClubDetailPattern(), config.getClubCalendarTemplate());
        Set<Integer> clubIds = parser.fetchClubIds();
        log.debug("Club ids were {}", clubIds);
        log.debug("There were {} total clubs", clubIds.size());
    }

    @Test
    public void tryFetchClassSchedules() {
        LocalDate weekStart = (new LocalDate()).dayOfWeek().withMinimumValue();
        TwentyFourHourParser parser = new TwentyFourHourParser(config.getClubListBaseUrl(), config.getClubDetailPattern(), config.getClubCalendarTemplate());
        Set<ClassInfo> schedules = parser.fetchClassSchedules(592, weekStart);
        log.debug("Schedules were {}", schedules);
        log.debug("There were {} total schedules", schedules.size());
    }

    @Test
    public void tryFetchAllClassSchedules() {
        LocalDate weekStart = (new LocalDate()).dayOfWeek().withMinimumValue();
        TwentyFourHourParser parser = new TwentyFourHourParser(config.getClubListBaseUrl(), config.getClubDetailPattern(), config.getClubCalendarTemplate());
        Set<ClassInfo> schedules = Sets.newHashSet();
        Set<Integer> clubIds = parser.fetchClubIds();
        for (Integer clubId: clubIds) {
            Set<ClassInfo> clubSchedules = parser.fetchClassSchedules(clubId, weekStart);
            schedules.addAll(clubSchedules);
        }
        log.debug("There were {} total schedules", schedules.size());
    }

}
