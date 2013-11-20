package com.lowtuna.gymclasscal;

import com.lowtuna.gymclasscal.business.ClassScheduleManager;
import com.lowtuna.gymclasscal.business.TwentyFourHourParser;
import com.lowtuna.gymclasscal.config.GymClassCalConfig;
import com.lowtuna.gymclasscal.jersey.ClassInfoResource;
import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

public class GymClassCalApplication extends Application<GymClassCalConfig> {

    public static void main (String[] args) throws Exception {
        if (args[args.length - 1].startsWith("~")) {
            args[args.length - 1] = System.getProperty("user.home") + args[args.length - 1].substring(1);
        }

        new GymClassCalApplication().run(args);
    }

    @Override
    public String getName() {
        return "GymClassCal";
    }

    @Override
    public void initialize(Bootstrap<GymClassCalConfig> bootstrap) {

    }

    @Override
    public void run(GymClassCalConfig configuration, Environment environment) throws Exception {
        TwentyFourHourParser parser = new TwentyFourHourParser(configuration.getClubListBaseUrl(), configuration.getClubDetailPattern(), configuration.getClubCalendarTemplate(), environment.metrics(), configuration.getClubIdsUpdateDuration());
        environment.healthChecks().register("24 Hour Fitness Schedule Parser", parser);

        ClassScheduleManager scheduleManager = new ClassScheduleManager(parser, configuration.getNumberOfWeekToLoad());

        ClassInfoResource classInfoResource = new ClassInfoResource(scheduleManager, parser);
        environment.jersey().register(classInfoResource);
    }
}
