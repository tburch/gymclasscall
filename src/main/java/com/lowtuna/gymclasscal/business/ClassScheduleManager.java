package com.lowtuna.gymclasscal.business;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.lowtuna.gymclasscal.core.ClassInfo;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.LocalDate;

@Slf4j
public class ClassScheduleManager {
    private final TwentyFourHourParser parser;
    private final int numWeeks;
    private final ExecutorService executorService;

    public ClassScheduleManager(TwentyFourHourParser parser, int numWeeks, ExecutorService executorService) {
        this.parser = parser;
        this.numWeeks = numWeeks;
        this.executorService = executorService;
    }

    public Set<ClassInfo> getClassInfos(final int clubId) {
        List<Future<Collection<ClassInfo>>> futures = Lists.newArrayList();

        for (int i = 0; i < numWeeks; i++) {
            final LocalDate weekStart = (new LocalDate()).dayOfWeek().withMinimumValue().plusWeeks(i);

            Future<Collection<ClassInfo>> classInfoFuture = executorService.submit(new Callable<Collection<ClassInfo>>() {
                @Override
                public Collection<ClassInfo> call() throws Exception {
                    return parser.fetchClassSchedules(clubId, weekStart);
                }
            });
            futures.add(classInfoFuture);
        }

        Set<ClassInfo> classes = Sets.newHashSet();
        for (Future<Collection<ClassInfo>> future: futures) {
            try {
                classes.addAll(future.get(5, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                log.warn("Interrupted while trying to get classes for clubId={}", clubId, e);
            } catch (ExecutionException e) {
                log.warn("Exception while trying to get classes for clubId={}", clubId, e);
            } catch (TimeoutException e) {
                log.warn("Timed out while trying to get classes for clubId={}", clubId, e);
            }
        }
        return classes;
    }

}