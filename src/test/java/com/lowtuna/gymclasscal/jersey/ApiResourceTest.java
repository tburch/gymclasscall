package com.lowtuna.gymclasscal.jersey;

import java.util.Collection;

import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTimeZone;
import org.junit.Test;

@Slf4j
public class ApiResourceTest {

    @Test
    public void test() {
        Collection<String> ids = DateTimeZone.getAvailableIDs();
        ApiResourceTest.log.info("ids={}", ids);
    }
}
