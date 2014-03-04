package com.lowtuna.gymclasscal;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;

import com.lowtuna.dropwizard.extras.heroku.AntiIdlerBundle;
import com.lowtuna.dropwizard.extras.heroku.AntiIdlerConfig;
import com.lowtuna.gymclasscal.business.ClassScheduleManager;
import com.lowtuna.gymclasscal.business.TwentyFourHourParser;
import com.lowtuna.gymclasscal.config.GymClassCalConfig;
import com.lowtuna.gymclasscal.jersey.ApiResource;
import com.lowtuna.gymclasscal.jersey.RequestIdFilter;
import com.lowtuna.gymclasscal.util.JsoupDocumentLoader;
import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.views.ViewBundle;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

@Slf4j
public class GymClassCalApplication extends Application<GymClassCalConfig> {

    public static void main (String[] args) throws Exception {
        if (args[args.length - 1].startsWith("~")) {
            args[args.length - 1] = System.getProperty("user.home") + args[args.length - 1].substring(1);
        }

        GymClassCalApplication.log.info("Application Config:");
        List<String> configFileLines = FileUtils.readLines(new File(args[args.length - 1]));

        for (String configLine: configFileLines) {
            GymClassCalApplication.log.info(configLine);
        }

        log.info("Running GymClassCalApplication");
        new GymClassCalApplication().run(args);
    }

    @Override
    public String getName() {
        return "GymClassCal";
    }

    @Override
    public void initialize(Bootstrap<GymClassCalConfig> bootstrap) {
        bootstrap.addBundle(new ViewBundle());
        bootstrap.addBundle(new AntiIdlerBundle<GymClassCalConfig>() {
            @Override
            public AntiIdlerConfig getConfig(GymClassCalConfig configuration) {
                return configuration.getAntiIdler();
            }
        });
    }

    @Override
    public void run(GymClassCalConfig configuration, Environment environment) throws Exception {
        JsoupDocumentLoader documentLoader = new JsoupDocumentLoader(environment.metrics());
        TwentyFourHourParser parser = new TwentyFourHourParser(configuration.getClubListBaseUrl(), configuration.getClubDetailPattern(), configuration.getClubCalendarTemplate(), environment.metrics(), configuration.getClubIdsUpdateDuration(), documentLoader);
        environment.healthChecks().register("24 Hour Fitness Schedule Parser", parser);

        ExecutorService scheduleManagerExecutorService = environment.lifecycle().executorService("scheduleManagerExecutorService-%d").maxThreads(80).build();
        ClassScheduleManager scheduleManager = new ClassScheduleManager(parser, configuration.getNumberOfWeekToLoad(), scheduleManagerExecutorService);

        ApiResource apiResource = new ApiResource(scheduleManager, parser, environment.metrics());
        environment.jersey().register(apiResource);

        environment.jersey().getResourceConfig().getContainerRequestFilters().add(RequestIdFilter.class);
    }
}
