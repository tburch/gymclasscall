package com.lowtuna.gymclasscal.config;

import com.damnhandy.uri.template.MalformedUriTemplateException;
import com.damnhandy.uri.template.UriTemplate;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

@Getter
@Setter
@Slf4j
public class GymClassCalConfig extends Configuration {
    @JsonProperty
    @NotEmpty
    private String baseUrl = "http://www.24hourfitness.com/ClubList";

    @JsonProperty
    @NotEmpty
    private String clubDetailPattern = "^/FindClubDetail\\.mvc\\?clubid=(\\d+)$";

    @JsonProperty
    @NotEmpty
    private String clubCalendarUriTemplate = "http://24hourfit.schedulesource.com/public/gxschedule.aspx{?club,date}";

    public UriTemplate getClubCalendarUriTemplate() {
        try {
            return UriTemplate.fromTemplate(clubCalendarUriTemplate);
        } catch (MalformedUriTemplateException e) {
            log.warn("Couldn't create UriTemplate from template={}", clubCalendarUriTemplate, e);
            throw new RuntimeException("Couldn't create UriTemplate from template=" + clubCalendarUriTemplate);
        }
    }
}
