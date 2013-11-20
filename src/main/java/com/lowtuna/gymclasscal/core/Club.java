package com.lowtuna.gymclasscal.core;

import lombok.Data;
import lombok.experimental.Builder;

@Builder
@Data
public class Club {
    private final String name;
    private final int id;
    private final String address;
    private final String phoneNumber;
}
