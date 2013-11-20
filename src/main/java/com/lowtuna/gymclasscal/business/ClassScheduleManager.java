package com.lowtuna.gymclasscal.business;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Sets;
import com.lowtuna.gymclasscal.core.ClassInfo;
import lombok.EqualsAndHashCode;
import org.joda.time.LocalDate;

public class ClassScheduleManager {
    private final LoadingCache<ClubScheduleClassKey, Set<ClassInfo>> classCache = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.DAYS)
            .recordStats()
            .build(new CacheLoader<ClubScheduleClassKey, Set<ClassInfo>>() {
                @Override
                public Set<ClassInfo> load(ClubScheduleClassKey key) throws Exception {
                    return parser.fetchClassSchedules(key.clubId, key.weekStart);
                }
            });

    private final TwentyFourHourParser parser;
    private final int numWeeks;

    public ClassScheduleManager(TwentyFourHourParser parser, int numWeeks) {
        this.parser = parser;
        this.numWeeks = numWeeks;
    }

    public Set<ClassInfo> getClassInfos(int clubId) {
        Set<ClassInfo> classes = Sets.newHashSet();
        for (int i = 0; i < numWeeks; i++) {
            LocalDate week = new LocalDate().plusWeeks(i);
            ClubScheduleClassKey key = new ClubScheduleClassKey(clubId, week);
            classes.addAll(classCache.getUnchecked(key));
        }
        return classes;
    }

    @EqualsAndHashCode
    private class ClubScheduleClassKey {
        private final int clubId;
        private final LocalDate weekStart;

        public ClubScheduleClassKey(int clubId, LocalDate weekStart) {
            this.clubId = clubId;
            this.weekStart = weekStart.dayOfWeek().withMinimumValue();
            ;
        }
    }
}
