package com.lowtuna.gymclasscal.core;

import lombok.Data;
import lombok.experimental.Builder;
import org.joda.time.LocalDateTime;

@Data
@Builder
public class ClassInfo {
    private final LocalDateTime time;
    private final String name;
    private final String instructor;
}
