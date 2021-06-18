package com.github.sajjaadalipour.ratelimit.generators;

import com.github.sajjaadalipour.ratelimit.conf.properties.RateLimitProperties.Policy;
import com.github.sajjaadalipour.ratelimit.exception.FieldNotPresentedException;
import com.github.sajjaadalipour.ratelimit.exception.HeaderNotPresentedException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.DelegatingServletInputStream;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link RequestBodyBasedKeyGenerator}.
 *
 * @author Anbarasan
 */
class RequestBodyBasedKeyGeneratorTest {

    @Test
    void generateKey_GivenNullHeader_ShouldThrownHeaderNotPresentedException() {
        RequestBodyBasedKeyGenerator keyGenerator = new RequestBodyBasedKeyGenerator(Collections.singleton("phone-number"));
        HttpServletRequest httpServletRequestMock = Mockito.mock(HttpServletRequest.class);

        Policy policy = new Policy(Duration.ofHours(1), 3, "TEST", null, null, null);

        Assertions.assertThrows(FieldNotPresentedException.class, () -> keyGenerator.generateKey(httpServletRequestMock, policy));
    }

    @Test
    void generateKey_GivenOneParam_ShouldReturnAKeyWithCombinationOf5Things() {
        RequestBodyBasedKeyGenerator keyGenerator = new RequestBodyBasedKeyGenerator(new HashSet<>(Arrays.asList("phone-number")));
        HttpServletRequest httpServletRequestMock = Mockito.mock(HttpServletRequest.class);

        String json = "{\"phone-number\":213213213, \"User-Id\":222}";
        try {
            Mockito.when(httpServletRequestMock.getInputStream()).thenReturn(
                    new DelegatingServletInputStream(
                            new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            Mockito.when(httpServletRequestMock.getReader()).thenReturn(
                    new BufferedReader(new StringReader(json)));
        } catch (IOException e) {
            e.printStackTrace();
        }

        Mockito.when(httpServletRequestMock.getRequestURI()).thenReturn("/test");
        Mockito.when(httpServletRequestMock.getMethod()).thenReturn("GET");

        Policy policy = new Policy(Duration.ofHours(1), 3, "TEST", null, null, null);

        String generatedKey = keyGenerator.generateKey(httpServletRequestMock, policy);

        assertEquals("/test_GET_PT1H_3_213213213", generatedKey);
        Assertions.assertEquals(5, generatedKey.split("_").length);
    }

    @Test
    void generateKey_GivenTwoParam_ShouldReturnAKey_WithCombinationOf6Things() throws IOException {
        RequestBodyBasedKeyGenerator keyGenerator = new RequestBodyBasedKeyGenerator(new HashSet<>(Arrays.asList("phone-number", "User-Id")));
        HttpServletRequest httpServletRequestMock = Mockito.mock(HttpServletRequest.class);

        String json = "{\"phone-number\":213213213, \"User-Id\":222}";
        Mockito.when(httpServletRequestMock.getInputStream()).thenReturn(
                new DelegatingServletInputStream(
                        new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));
        Mockito.when(httpServletRequestMock.getReader()).thenReturn(
                new BufferedReader(new StringReader(json)));

        Mockito.when(httpServletRequestMock.getRequestURI()).thenReturn("/test");
        Mockito.when(httpServletRequestMock.getMethod()).thenReturn("GET");

        Policy policy = new Policy(Duration.ofHours(1), 3, "TEST", null, null, null);

        String generatedKey = keyGenerator.generateKey(httpServletRequestMock, policy);
        assertEquals("/test_GET_PT1H_3_213213213_222", generatedKey);

        Assertions.assertEquals(6, generatedKey.split("_").length);
    }
}