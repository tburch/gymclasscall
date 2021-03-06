package com.lowtuna.gymclasscal.config;

import com.damnhandy.uri.template.MalformedUriTemplateException;
import com.damnhandy.uri.template.UriTemplate;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.jaxrs.json.annotation.JSONP;
import com.lowtuna.dropwizard.extras.config.ElasticSearchConfig;
import com.lowtuna.dropwizard.extras.heroku.AntiIdlerConfig;
import io.dropwizard.Configuration;
import io.dropwizard.util.Duration;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.NotNull;

@Getter
@Setter
@Slf4j
public class GymClassCalConfig extends Configuration {
    @JsonProperty
    private Duration clubIdsUpdateDuration = null;

    @JsonProperty
    private int numberOfWeekToLoad = 4;

    @JsonProperty
    @NotEmpty
    private String clubListBaseUrl = "http://www.24hourfitness.com/ClubList";

    @JsonProperty
    @NotEmpty
    private String clubDetailPattern = "^/FindClubDetail\\.mvc\\?clubid=(\\d+)$";

    @JsonProperty
    @NotEmpty
    private String clubCalendarTemplate = "http://24hourfit.schedulesource.com/public/gxschedule.aspx{?club,date}";

    @JsonProperty
    @NotNull
    private ElasticSearchConfig elasticSearch = new ElasticSearchConfig();

    @JsonProperty
    @NotNull
    private AntiIdlerConfig antiIdler = new AntiIdlerConfig();

    public UriTemplate getClubCalendarTemplate() {
        try {
            return UriTemplate.fromTemplate(clubCalendarTemplate);
        } catch (MalformedUriTemplateException e) {
            log.warn("Couldn't create UriTemplate from template={}", clubCalendarTemplate, e);
            throw new RuntimeException("Couldn't create UriTemplate from template=" + clubCalendarTemplate);
        }
    }
}
