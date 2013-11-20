package com.lowtuna.gymclasscal.business;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.damnhandy.uri.template.UriTemplate;
import com.damnhandy.uri.template.VariableExpansionException;
import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.lowtuna.gymclasscal.core.ClassInfo;
import com.lowtuna.gymclasscal.core.Club;
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
public class TwentyFourHourParser {
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

    private final String baseClubListUrl;
    private final Pattern clubDetailPagePattern;
    private final UriTemplate clubCalendarUriTemplate;

    public TwentyFourHourParser(String baseClubListUrl, String clubDetailPagePattern, UriTemplate clubCalendarUriTemplate) {
        this.baseClubListUrl = baseClubListUrl;
        this.clubCalendarUriTemplate = clubCalendarUriTemplate;
        this.clubDetailPagePattern = Pattern.compile(clubDetailPagePattern);
    }

    public Set<ClassInfo> fetchClassSchedules(int clubId, int numWeeks) {
        Set<ClassInfo> classes = Sets.newHashSet();
        Club club = null;
        for (int i = 0; i < numWeeks; i++) {
            LocalDate weekStart = (new LocalDate()).dayOfWeek().withMinimumValue().plusWeeks(i);
            TwentyFourHourParser.log.debug("Getting class schedule for week starting {} for club with id={}", weekStart, clubId);
            try {
                String fullUri = clubCalendarUriTemplate
                        .set("club", clubId)
                        .set("date", DATE_PARAM_FORMATTER.print(weekStart))
                        .expand();
                Document clubCalDoc = Jsoup.connect(fullUri).get();

                if (club == null) {
                    Elements clubDetails = clubCalDoc.select("body div:eq(1) > table > tbody > tr:eq(1) > td > div");
                    if (!clubDetails.isEmpty()) {
                        Matcher matcher = CLUB_DETAILS_PATTERN.matcher(clubDetails.iterator().next().ownText());
                        if (matcher.matches()) {
                            club = Club.builder().address(matcher.group(2).trim()).id(clubId).phoneNumber(matcher.group(3).trim()).name(matcher.group(1).trim()).build();
                            TwentyFourHourParser.log.debug("Parsed club to {}", club);
                        }
                    }
                }

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
                            TwentyFourHourParser.log.debug("Found class time of {}", classDateTime);

                            List<ClassInfo> classInfoEntries = extractClassScheduleEntries(element, classDateTime);
                            classes.addAll(classInfoEntries);
                        }
                    }
                }
            } catch (VariableExpansionException e) {
                log.error("Couldn't create club calendar schedule for clubId={} and date={}", club, weekStart, e);
            } catch (IOException e) {
                log.error("Couldn't access club calendar schedule for clubId={} and date={}", club, weekStart, e);
            }
        }
        return classes;
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

            String details[] = StringUtils.splitByWholeSeparator(classEl.text(), " ");
            String instructor = details[details.length - 1];

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

    public Set<Integer> fetchClubIds() {
        try {
            URI baseUri = new URI(baseClubListUrl);
            Set<String> stateLinks = getAllStateLinks(baseUri);
            Set<String> cityLinks = getAllCityLinks(stateLinks, baseUri);
            return parseCityLinks(cityLinks);
        } catch (URISyntaxException e) {
            log.warn("Couldn't create URI for {}. Returning empty set of club ids", baseClubListUrl, e);
            return Collections.emptySet();
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
}
