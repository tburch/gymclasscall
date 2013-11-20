package com.lowtuna.gymclasscal.jersey;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import biweekly.Biweekly;
import biweekly.ICalendar;
import biweekly.component.VEvent;
import biweekly.property.DateStart;
import biweekly.property.Location;
import biweekly.property.ProductId;
import biweekly.property.Summary;
import biweekly.util.Duration;
import com.codahale.metrics.annotation.Timed;
import com.lowtuna.gymclasscal.business.ClassScheduleManager;
import com.lowtuna.gymclasscal.business.TwentyFourHourParser;
import com.lowtuna.gymclasscal.core.ClassInfo;
import com.lowtuna.gymclasscal.core.Club;
import io.dropwizard.jersey.caching.CacheControl;
import lombok.extern.slf4j.Slf4j;
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
    public Response getCalendar(@PathParam("clubId") final int clubId,
                                @DefaultValue("America/Denver") @QueryParam("timeZone") final String timeZone) {
        return Response.ok(new StreamingOutput() {
            @Override
            public void write(OutputStream output) throws IOException, WebApplicationException {
                Collection<ClassInfo> allClasses = scheduleManager.getClassInfos(clubId);
                Club club = parser.fetchClubInfo(clubId);
                ICalendar iCal = new ICalendar();
                iCal.setProperty(new Location(club.getAddress()));
                iCal.setProductId(new ProductId("//GymClassCal//EN"));
                iCal.addExperimentalProperty("X-WR-CALNAME", "24 Hour Fitness Class Schedule - " + club.getName());
                for (ClassInfo classInfo: allClasses) {
                    VEvent event = new VEvent();

                    Summary summary = event.setSummary(classInfo.getName());
                    summary.setLanguage("en-us");

                    Date start = new Date(classInfo.getTime().toDateTime(DateTimeZone.forID(timeZone)).getMillis());
                    DateStart dateStart = new DateStart(start, true);
                    dateStart.setTimezoneId(timeZone);
                    event.setDateStart(dateStart);
                    event.setDuration(Duration.builder().hours(1).build());

                    iCal.addEvent(event);
                }
                Biweekly.write(iCal).go(output);
            }
        }).type("text/calendar").build();
    }

    @GET
    @Timed
    @Path("club/{clubId}")
    @CacheControl(maxAge = 12, maxAgeUnit = TimeUnit.HOURS)
    public Response getCalendar(@PathParam("clubId") final int clubId) {
        return Response.ok().entity(parser.fetchClubInfo(clubId)).build();
    }
}
