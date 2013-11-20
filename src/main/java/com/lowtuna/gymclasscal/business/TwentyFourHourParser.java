package com.lowtuna.gymclasscal.business;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.concurrent.GuardedBy;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.codahale.metrics.health.HealthCheck;
import com.damnhandy.uri.template.UriTemplate;
import com.damnhandy.uri.template.VariableExpansionException;
import com.google.common.base.CaseFormat;
import com.google.common.base.Stopwatch;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.lowtuna.gymclasscal.core.ClassInfo;
import com.lowtuna.gymclasscal.core.Club;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.util.Duration;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

@Slf4j
public class TwentyFourHourParser extends HealthCheck implements Managed {
    private static final DateTimeFormatter DATE_PARAM_FORMATTER = DateTimeFormat.forPattern("dd/MM/yyyy");
    private static final DateTimeFormatter CALENDAR_DATE_FORMATTER = DateTimeFormat.forPattern("MMM-d");
    private static final Pattern CALENDAR_DATE_PATTERN = Pattern.compile("^\\w+ ([A-Za-z]{3}\\-[\\d]{2})$");
    private static final DateTimeFormatter CALENDAR_TIME_FORMATTER = DateTimeFormat.forPattern("h:mma");
    private static final DateTimeFormatter CALENDAR_WEEK_FORMATTER = DateTimeFormat.forPattern("MMMM dd, yyyy");
    private static final Pattern CLUB_DETAILS_PATTERN = Pattern.compile("(.*):(.*[\\d]{5}).*([\\d]{3}-[\\d]{3}-[\\d]{4}).*");
    private static Map<String, String> IMAGE_TO_CLASS_NAME;
    static {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();

        builder.put("../Images/24Hour/BODYPUMP.jpg", "Body Pump");
        builder.put("../Images/24Hour/TURBO2010.jpg", "Turbo Kick");
        builder.put("../Images/24Hour/bodycombatlogo.jpg", "Body Combat");
        builder.put("../Images/24Hour/zumba.jpg", "Zumba");

        IMAGE_TO_CLASS_NAME = builder.build();
    }

    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private final ReentrantReadWriteLock clubIdsLock = new ReentrantReadWriteLock();
    private final LoadingCache<String, Document> clubCalendarDocumentCache = CacheBuilder.newBuilder()
            .expireAfterWrite(12, TimeUnit.HOURS)
            .recordStats()
            .build(new CacheLoader<String, Document>() {
                @Override
                public Document load(String key) throws Exception {
                    Timer.Context timerContext = clubCalendarRequestTimer.time();
                    try {
                        return Jsoup.connect(key).get();
                    } finally {
                        timerContext.stop();
                    }
                }
            });

    private final String baseClubListUrl;
    private final Pattern clubDetailPagePattern;
    private final UriTemplate clubCalendarUriTemplate;
    private final Timer clubIdsUpdateTimer;
    private final Timer clubCalendarRequestTimer;

    @GuardedBy("clubIdsLock")
    private Set<Integer> clubIds = Sets.newCopyOnWriteArraySet();

    public TwentyFourHourParser(String baseClubListUrl, String clubDetailPagePattern, UriTemplate clubCalendarUriTemplate, MetricRegistry metricRegistry, Duration clubIdsUpdateDuration) {
        this.baseClubListUrl = baseClubListUrl;
        this.clubCalendarUriTemplate = clubCalendarUriTemplate;
        this.clubDetailPagePattern = Pattern.compile(clubDetailPagePattern);

        this.clubIdsUpdateTimer = metricRegistry.timer(MetricRegistry.name(getClass(), "updateClubIds"));
        this.clubCalendarRequestTimer = metricRegistry.timer(MetricRegistry.name(getClass(), "loadClubCalendar"));

        metricRegistry.register(MetricRegistry.name(getClass(), "clubCount"), new Gauge<Integer>() {
            @Override
            public Integer getValue() {
                return getClubIds().size();
            }
        });

        metricRegistry.register(MetricRegistry.name(getClass(), "clubCalendarDocumentCache", "size"), new Gauge<Long>() {
            @Override
            public Long getValue() {
                return clubCalendarDocumentCache.size();
            }
        });
        metricRegistry.register(MetricRegistry.name(getClass(), "clubCalendarDocumentCache", "hits"), new Gauge<Long>() {
            @Override
            public Long getValue() {
                return clubCalendarDocumentCache.stats().hitCount();
            }
        });
        metricRegistry.register(MetricRegistry.name(getClass(), "clubCalendarDocumentCache", "misses"), new Gauge<Long>() {
            @Override
            public Long getValue() {
                return clubCalendarDocumentCache.stats().missCount();
            }
        });
        metricRegistry.register(MetricRegistry.name(getClass(), "clubCalendarDocumentCache", "evictions"), new Gauge<Long>() {
            @Override
            public Long getValue() {
                return clubCalendarDocumentCache.stats().evictionCount();
            }
        });
        metricRegistry.register(MetricRegistry.name(getClass(), "clubCalendarDocumentCache", "loadPenalty"), new Gauge<Double>() {
            @Override
            public Double getValue() {
                return clubCalendarDocumentCache.stats().averageLoadPenalty();
            }
        });

        if (clubIdsUpdateDuration != null) {
            scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    log.debug("Updating all club Ids");
                    Set<Integer> newClubIds = fetchCLubIds();
                    if (newClubIds.isEmpty()) {
                        log.warn("Newly fetched club ids was empty!");
                        return;
                    }
                    clubIdsLock.writeLock().lock();
                    try {
                        clubIds.clear();
                        clubIds.addAll(newClubIds);
                    } finally {
                        clubIdsLock.writeLock().unlock();
                    }
                }
            }, 0, clubIdsUpdateDuration.getQuantity(), clubIdsUpdateDuration.getUnit());
        }
    }

    public Club fetchClubInfo(int clubId) {
        Club club = null;
        TwentyFourHourParser.log.info("Getting club info for club with id={}", clubId);
        try {
            LocalDate weekStart = (new LocalDate()).dayOfWeek().withMinimumValue();
            String fullUri = clubCalendarUriTemplate
                    .set("club", clubId)
                    .set("date", DATE_PARAM_FORMATTER.print(weekStart))
                    .expand();
            Document clubCalDoc = clubCalendarDocumentCache.get(fullUri);

            if (club == null) {
                Elements clubDetails = clubCalDoc.select("body div:eq(1) > table > tbody > tr:eq(1) > td > div");
                if (!clubDetails.isEmpty()) {
                    Matcher matcher = CLUB_DETAILS_PATTERN.matcher(clubDetails.iterator().next().ownText());
                    if (matcher.matches()) {
                        club = Club.builder().address(matcher.group(2).trim().replaceAll("[^\\u0000-\\uFFFF]", "")).clubId(clubId).phoneNumber(matcher.group(3).trim().replaceAll("[^\\u0000-\\uFFFF]", "")).name(matcher.group(1).trim().replaceAll("[^\\u0000-\\uFFFF]", "")).build();
                        TwentyFourHourParser.log.debug("Parsed club to {}", club);
                    }
                }
            }
        } catch (VariableExpansionException e) {
            log.error("Couldn't create club calendar schedule for clubId={}", clubId, e);
        } catch (ExecutionException e) {
            log.error("Couldn't get club calendar schedule for clubId={}", clubId, e);
        }
        return club;
    }

    public Set<ClassInfo> fetchClassSchedules(int clubId, LocalDate weekStart) {
        Set<ClassInfo> classes = Sets.newHashSet();
        TwentyFourHourParser.log.info("Getting class schedule for week starting {} for club with id={}", weekStart, clubId);
        try {
            String fullUri = clubCalendarUriTemplate
                    .set("club", clubId)
                    .set("date", DATE_PARAM_FORMATTER.print(weekStart))
                    .expand();
            Document clubCalDoc = clubCalendarDocumentCache.get(fullUri);

            Elements weekOfEls = clubCalDoc.select("#WeekTitle");
            if (!weekOfEls.isEmpty()) {
                DateTime weekDateTime = CALENDAR_WEEK_FORMATTER.parseDateTime(weekOfEls.iterator().next().html());

                Map<Integer, LocalDate> columnDate = Maps.newHashMap();
                Elements columns = clubCalDoc.select("#cal > tbody > tr > td > table > tbody > tr:eq(0) > td");
                populateColumnDates(weekDateTime, columnDate, columns);

                for (Element row: clubCalDoc.select("#cal > tbody > tr > td > table > tbody > tr:gt(0)")) {
                    columns = row.select("td");
                    DateTime time = null;
                    for(int columnNdx = 0; columnNdx < columns.size(); columnNdx++) {
                        Element element = columns.get(columnNdx);
                        if (columnNdx == 0 && element.hasClass("hours")) {
                            time = CALENDAR_TIME_FORMATTER.parseDateTime(element.text());
                            continue;
                        }

                        if (time == null) {
                            TwentyFourHourParser.log.error("Time for the row was null!");
                            break;
                        }

                        LocalDate date = columnDate.get(columnNdx);

                        LocalDateTime classDateTime = new LocalDateTime(date.getYear(), date.getMonthOfYear(), date.getDayOfMonth(), time.getHourOfDay(), time.getMinuteOfHour());

                        List<ClassInfo> classInfoEntries = extractClassScheduleEntries(element, classDateTime);
                        classes.addAll(classInfoEntries);
                    }
                }
            }
        } catch (VariableExpansionException e) {
            log.error("Couldn't create club calendar schedule for clubId={} and date={}", clubId, weekStart, e);
        } catch (ExecutionException e) {
            log.error("Couldn't access club calendar schedule for clubId={} and date={}", clubId, weekStart, e);
        }
        return classes;
    }

    public Set<Integer> getClubIds() {
        clubIdsLock.readLock().lock();
        try {
            return ImmutableSet.copyOf(clubIds);
        } finally {
            clubIdsLock.readLock().unlock();
        }
    }

    private Set<Integer> fetchCLubIds() {
        Stopwatch stopwatch = Stopwatch.createStarted();
        try {
            URI baseUri = new URI(baseClubListUrl);
            Set<String> stateLinks = getAllStateLinks(baseUri);
            Set<String> cityLinks = getAllCityLinks(stateLinks, baseUri);
            return parseCityLinks(cityLinks);
        } catch (URISyntaxException e) {
            log.warn("Couldn't create URI for {}. Returning empty set of club ids", baseClubListUrl, e);
            return Collections.emptySet();
        } finally {
            clubIdsUpdateTimer.update(stopwatch.elapsed(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS);
            log.debug("Completed fetching club ids in {}MS", stopwatch.elapsed(TimeUnit.MILLISECONDS));
        }
    }

    private List<ClassInfo> extractClassScheduleEntries(Element element, LocalDateTime classDateTime) {
        List<ClassInfo> entries = Lists.newArrayList();
        for (Element classEl: element.select("span")) {
            Elements classLink = classEl.select("a");
            if (classLink.isEmpty()) {
                continue;
            }

            String className;
            if (StringUtils.isEmpty(classLink.text())) {
                String link = classLink.select("img").attr("src");
                className = IMAGE_TO_CLASS_NAME.get(link);
                if (className == null) {
                    className = link.substring(link.lastIndexOf("/") + 1, link.lastIndexOf("."));
                    className = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, className);
                }
            } else {
                className = classLink.text();
            }

            String stripped = classEl.html().replaceAll("<[^>]*>", "\r");
            String details[] = StringUtils.splitByWholeSeparator(stripped, "\r");
            String instructor = StringUtils.isEmpty(details[details.length - 1]) ? details[0] : details[details.length - 1];

            entries.add(ClassInfo.builder().name(className).time(classDateTime).instructor(instructor).build());

        }
        return entries;
    }

    private void populateColumnDates(DateTime weekDateTime, Map<Integer, LocalDate> columnDate, Elements columns) {
        for (int columnNdx = 0; columnNdx < columns.size(); columnNdx++) {
            String dateString = columns.get(columnNdx).text();
            Matcher matcher = CALENDAR_DATE_PATTERN.matcher(dateString);
            if (matcher.matches()) {
                LocalDate date = CALENDAR_DATE_FORMATTER.parseLocalDate(matcher.group(1));
                date = date.year().setCopy(weekDateTime.getYear());
                columnDate.put(columnNdx, date);
            }
        }
    }

    private Set<String> getAllStateLinks(URI baseUri) {
        Set<String> stateLinks = Sets.newHashSet();
        try {
            Document stateListDoc = Jsoup.connect(baseClubListUrl).get();
            Elements allStateLinks = stateListDoc.select("a");
            stateLinks.addAll(extractLinks(allStateLinks, baseUri.getPath()));
        } catch (IOException e) {
            log.error("Couldn't get all state links for {}", baseClubListUrl, e);
        }
        return stateLinks;
    }

    private Set<String> getAllCityLinks(Set<String> stateLinks, URI baseUri) {
        Set<String> cityLinks = Sets.newHashSet();
        for (String stateLink: stateLinks) {
            try {
                TwentyFourHourParser.log.debug("Following state link {}", stateLink);
                Document cityListDoc = Jsoup.connect(stateLink).get();
                Elements allCityLinks = cityListDoc.select("a");
                cityLinks.addAll(extractLinks(allCityLinks, baseUri.getPath()));
            } catch (IOException e) {
                log.error("Couldn't get city links for {}", stateLink, e);
            }
        }
        return cityLinks;
    }

    private Set<Integer> parseCityLinks(Set<String> cityLinks) {
        Set<Integer> clubIds = Sets.newHashSet();
        for (String cityLink: cityLinks) {
            try {
                TwentyFourHourParser.log.debug("Following city link {}", cityLink);
                Document cityClubsDoc = Jsoup.connect(cityLink).get();
                Elements clubListLinks  = cityClubsDoc.select("#clubListTable tr.oddRow td a, #clubListTable tr.evenRow td a");

                for (Element clubDetailsEl: clubListLinks) {
                    String link = clubDetailsEl.attr("href");
                    if (StringUtils.isEmpty(link)) {
                        continue;
                    }
                    Matcher matcher = clubDetailPagePattern.matcher(link);
                    if (matcher.matches()) {
                        TwentyFourHourParser.log.debug("Found club details link {}", link);
                        Integer clubId = Integer.parseInt(matcher.group(1));
                        clubIds.add(clubId);
                        TwentyFourHourParser.log.debug("Found club ID of {}", clubId);
                    }
                }
            } catch (IOException e) {
                log.error("Couldn't get city link for {}", cityLink, e);
            }
        }
        return clubIds;
    }

    private static List<String> extractLinks(Elements linkEls, String prefixPath) {
        List<String> links = Lists.newArrayList();
        for (Element stateLinkElement: linkEls) {
            String link = stateLinkElement.attr("href");
            if (link.startsWith(prefixPath) && !link.equals(prefixPath)) {
                links.add(stateLinkElement.attr("abs:href"));
            }
        }
        return links;
    }

    @Override
    protected Result check() throws Exception {
        String fullUri = clubCalendarUriTemplate.expand();
        try {
            HttpURLConnection.setFollowRedirects(false);
            HttpURLConnection con = (HttpURLConnection) new URL(fullUri).openConnection();
            con.setRequestMethod("HEAD");
            if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {
                return HealthCheck.Result.healthy();
            } else {
                return Result.unhealthy("Didn't get a 200 response from " + fullUri);
            }
        } catch (Exception e) {
            log.warn("Couldn't connect to {}", fullUri, e);
            return Result.unhealthy("Couldn't connect to " + fullUri);
        }
    }

    @Override
    public void start() throws Exception {

    }

    @Override
    public void stop() throws Exception {

    }
}
