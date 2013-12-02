package com.lowtuna.gymclasscal;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.EnumSet;
import java.util.Set;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.FilterRegistration;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import com.google.common.collect.ImmutableSet;
import com.lowtuna.gymclasscal.business.ClassScheduleManager;
import com.lowtuna.gymclasscal.business.TwentyFourHourParser;
import com.lowtuna.gymclasscal.config.GymClassCalConfig;
import com.lowtuna.gymclasscal.jersey.ClassInfoResource;
import com.lowtuna.gymclasscal.jersey.RequestIdFilter;
import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.mortbay.servlet.ProxyServlet;

@Slf4j
public class GymClassCalApplication extends Application<GymClassCalConfig> {
    private static final String PORT_KEY = "PORTPORT";
    private static final String ADMIN = "/admin";
    private static final Set<String> METRICS_PATHS = ImmutableSet.<String>builder().add("/metrics", "/ping", "/threads", "/healthcheck").build();

    public static void main (String[] args) throws Exception {
        if (args[args.length - 1].startsWith("~")) {
            args[args.length - 1] = System.getProperty("user.home") + args[args.length - 1].substring(1);
        }

        if ("server".equalsIgnoreCase(args[0]) && "herokuFix".equalsIgnoreCase(args[1])) {
            File configFile = new File(args[args.length - 1]);
            String config = FileUtils.readFileToString(configFile);
            String port = System.getenv("PORT");
            log.info("Replacing all occurrences of {} with {} in {}", PORT_KEY, port, args[args.length - 1]);
            config = config.replace(PORT_KEY, port);
            FileUtils.write(configFile, config);

            //fix args to what DropWizard is expecting
            String[] correctArgs = new String[2];
            correctArgs[0] = args[0];
            correctArgs[1] = args[args.length - 1];
            args = correctArgs;
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
        //proxy servlet
        ServletRegistration.Dynamic adminProxyReg = environment.servlets().addServlet("admin proxy servlet", ProxyServlet.Transparent.class);
        adminProxyReg.setLoadOnStartup(1);
        adminProxyReg.setInitParameter("ProxyTo", "http://localhost:8081");
        adminProxyReg.setInitParameter("Prefix", ADMIN);
        adminProxyReg.addMapping(ADMIN, ADMIN + "/*");

        FilterRegistration.Dynamic adminFilterReg = environment.servlets().addFilter("admin metrics filter", new Filter() {
            @Override
            public void init(FilterConfig filterConfig) throws ServletException {
                //nothing to do
            }

            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
                HttpServletRequest req = (HttpServletRequest) request;
                String requestURI = req.getRequestURI();

                if (METRICS_PATHS.contains(requestURI)) {
                    req.getRequestDispatcher(ADMIN + requestURI).forward(req, response);
                } else {
                    chain.doFilter(req, response);
                }
            }

            @Override
            public void destroy() {
                //nothing to do
            }
        });
        adminFilterReg.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), false, METRICS_PATHS.toArray((String[]) Array.newInstance(String.class, METRICS_PATHS.size())));


        TwentyFourHourParser parser = new TwentyFourHourParser(configuration.getClubListBaseUrl(), configuration.getClubDetailPattern(), configuration.getClubCalendarTemplate(), environment.metrics(), configuration.getClubIdsUpdateDuration());
        environment.healthChecks().register("24 Hour Fitness Schedule Parser", parser);

        ClassScheduleManager scheduleManager = new ClassScheduleManager(parser, configuration.getNumberOfWeekToLoad());

        ClassInfoResource classInfoResource = new ClassInfoResource(scheduleManager, parser, environment.metrics());
        environment.jersey().register(classInfoResource);

        environment.jersey().getResourceConfig().getContainerRequestFilters().add(RequestIdFilter.class);
    }
}
