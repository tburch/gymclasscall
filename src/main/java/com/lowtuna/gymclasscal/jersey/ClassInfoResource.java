package com.lowtuna.gymclasscal.jersey;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.lowtuna.gymclasscal.business.ClassScheduleManager;
import com.lowtuna.gymclasscal.business.TwentyFourHourParser;
import com.lowtuna.gymclasscal.core.ClassInfo;
import com.lowtuna.gymclasscal.core.Club;
import io.dropwizard.jersey.caching.CacheControl;
import lombok.extern.slf4j.Slf4j;
import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.extensions.property.WrCalName;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.ParameterList;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.TimeZoneRegistryFactory;
import net.fortuna.ical4j.model.ValidationException;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.property.CalScale;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Version;
import org.joda.time.DateTimeZone;

@Path("api")
@Slf4j
public class ClassInfoResource {
    private final ClassScheduleManager scheduleManager;
    private final TwentyFourHourParser parser;

    public ClassInfoResource(ClassScheduleManager scheduleManager, TwentyFourHourParser parser) {
        this.scheduleManager = scheduleManager;
        this.parser = parser;
    }

    @GET
    @Timed
    @Path("club/{clubId}/classes.ical")
    @Produces("text/calendar")
    @CacheControl(maxAge = 12, maxAgeUnit = TimeUnit.HOURS)
    public Response getCalendar(@PathParam("clubId") int clubId,
                                @QueryParam("class") final List<String> classes,
                                @QueryParam("instructor") final List<String> instructors,
                                @DefaultValue("America/Denver") @QueryParam("timeZone") String timeZone) {
        Collection<ClassInfo> allClasses = scheduleManager.getClassInfos(clubId);

        log.debug("Found {} total classes before filtering", allClasses.size());

        allClasses = Collections2.filter(allClasses, new Predicate<ClassInfo>() {
            @Override
            public boolean apply(@Nullable ClassInfo input) {
                if (!classes.isEmpty()) {
                    return classes.contains(input.getName());
                }
                if (!instructors.isEmpty()) {
                    return instructors.contains(input.getInstructor());
                }
                return true;
            }
        });

        List<ClassInfo> filteredClasses = Lists.newArrayList(allClasses);

        log.debug("Found {} total classes after filtering", filteredClasses.size());

        Collections.sort(filteredClasses, new Comparator<ClassInfo>() {
            @Override
            public int compare(ClassInfo o1, ClassInfo o2) {
                return o1.getTime().compareTo(o2.getTime());
            }
        });

        Club club = parser.fetchClubInfo(clubId);
        TimeZoneRegistry registry = TimeZoneRegistryFactory.getInstance().createRegistry();

        final Calendar calendar = new Calendar();
        calendar.getProperties().add(new ProdId("-//Tristan Burch//GymClassCal 1.0//EN"));
        calendar.getProperties().add(Version.VERSION_2_0);
        calendar.getProperties().add(CalScale.GREGORIAN);
        VTimeZone tz = registry.getTimeZone(timeZone).getVTimeZone();
        calendar.getComponents().add(tz);
        calendar.getProperties().add(new WrCalName(new ParameterList(), WrCalName.FACTORY, "24 Hour Fitness - " + club.getName()));

        for (ClassInfo classInfo: filteredClasses) {
            DateTime start = new DateTime(classInfo.getTime().toDateTime(DateTimeZone.forID(timeZone)).toCalendar(Locale.US).getTime());
            VEvent event = new VEvent(start, new Dur("1H"), classInfo.getName());
            event.getProperties().add(new Uid(UUID.randomUUID().toString()));
            event.getProperties().add(new Location(club.getAddress()));
            calendar.getComponents().add(event);
        }

        return Response.ok(new StreamingOutput() {
            @Override
            public void write(OutputStream output) throws IOException, WebApplicationException {
                CalendarOutputter calendarOutputter = new CalendarOutputter();
                try {
                    calendarOutputter.output(calendar, output);
                } catch (ValidationException e) {
                    log.warn("iCal was invalid!", e);
                }
            }
        }).type("text/calendar").build();
    }

    @GET
    @Timed
    @Path("club/{clubId}")
    @CacheControl(maxAge = 12, maxAgeUnit = TimeUnit.HOURS)
    public Response getCalendar(@PathParam("clubId") int clubId) {
        return Response.ok().entity(parser.fetchClubInfo(clubId)).build();
    }
}
