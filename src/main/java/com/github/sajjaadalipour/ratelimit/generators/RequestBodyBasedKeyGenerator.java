package com.github.sajjaadalipour.ratelimit.generators;

import com.github.sajjaadalipour.ratelimit.RateLimitKeyGenerator;
import com.github.sajjaadalipour.ratelimit.conf.properties.RateLimitProperties.Policy;
import com.github.sajjaadalipour.ratelimit.exception.FieldNotPresentedException;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.util.StreamUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.StringJoiner;

/**
 * An implementation of {@link RateLimitKeyGenerator} to generate a identity key from the requester
 * based on HTTP Request body.
 *
 * @author Anbarasan
 */
public class RequestBodyBasedKeyGenerator implements RateLimitKeyGenerator {

    /**
     * Represents the defined params in the property file,
     * that used as HTTP request fields in {@link #generateKey(HttpServletRequest, Policy)}.
     */
    private final Set<String> params;

    public RequestBodyBasedKeyGenerator(Set<String> params) {
        this.params = params;
    }

    /**
     * Gets the request body by the {@link #params} and return a string value
     * from combination of given header parameters.
     * <p>
     * Makes a key by Http servlet request method and request URI.
     * <p>
     * Gets Http request body by given {@link #params} from the {@code servletRequest}.
     * <p>If any value of {@link #params} does not exists in Http request, throw exception.
     *
     * @param servletRequest Encapsulates the http servlet request.
     * @param policy         Encapsulates the rate limit policy properties.
     * @return Generated code.
     * @throws FieldNotPresentedException If not present any item of the given {@link #params} from the Http request body.
     */
    @Override
    public String generateKey(HttpServletRequest servletRequest, Policy policy) {
        StringJoiner key = new StringJoiner("_")
                .add(servletRequest.getRequestURI())
                .add(servletRequest.getMethod())
                .add(policy.getDuration().toString())
                .add(String.valueOf(policy.getCount()));

        String jsonString;
        try {
            InputStream inputStream = servletRequest.getInputStream();
            byte[] body = StreamUtils.copyToByteArray(inputStream);
            jsonString = new String(body);
        } catch (IOException e) {
            throw new FieldNotPresentedException("param", "The field `" +
                    "` is not presented in the request body " + servletRequest.getRequestURI());
        }
        for (String param : params) {
            String field = null;
            try {
                field = new JSONObject(jsonString).getString(param);
            } catch (JSONException e) {
                if (field == null) {
                    throw new FieldNotPresentedException(param, "The field `" + param +
                            "` is not presented in the request body " + servletRequest.getRequestURI());
                }
            }
            key.add(field);
        }
        return key.toString();
    }
}
