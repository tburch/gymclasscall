package com.lowtuna.gymclasscal.business;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClubManager {
    private final TwentyFourHourParser parser;

    public ClubManager(TwentyFourHourParser parser) {
        this.parser = parser;
    }

}
