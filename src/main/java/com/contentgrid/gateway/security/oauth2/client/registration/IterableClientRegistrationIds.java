package com.contentgrid.gateway.security.oauth2.client.registration;

import java.util.Iterator;
import java.util.stream.Stream;

public interface IterableClientRegistrationIds {

    Stream<String> registrationIds();

}
