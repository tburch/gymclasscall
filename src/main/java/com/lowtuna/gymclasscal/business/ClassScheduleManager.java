package com.lowtuna.gymclasscal.business;

import java.util.Set;

import com.google.common.collect.Sets;
import com.lowtuna.gymclasscal.core.ClassInfo;
import org.joda.time.LocalDate;

public class ClassScheduleManager {
    private final TwentyFourHourParser parser;
    private final int numWeeks;

    public ClassScheduleManager(TwentyFourHourParser parser, int numWeeks) {
        this.parser = parser;
        this.numWeeks = numWeeks;
    }

    public Set<ClassInfo> getClassInfos(int clubId) {
        Set<ClassInfo> classes = Sets.newHashSet();
        for (int i = 0; i < numWeeks; i++) {
            LocalDate weekStart = (new LocalDate()).dayOfWeek().withMinimumValue().plusWeeks(i);
            classes.addAll(parser.fetchClassSchedules(clubId, weekStart));
        }
        return classes;
    }

}