package com.github.sajjaadalipour.ratelimit.conf.filter;

import com.github.sajjaadalipour.ratelimit.Rate;
import com.github.sajjaadalipour.ratelimit.RateLimitKeyGenerator;
import com.github.sajjaadalipour.ratelimit.RateLimiter;
import com.github.sajjaadalipour.ratelimit.RatePolicy;
import com.github.sajjaadalipour.ratelimit.conf.error.TooManyRequestErrorHandler;
import com.github.sajjaadalipour.ratelimit.conf.properties.RateLimitProperties;
import com.github.sajjaadalipour.ratelimit.conf.properties.RateLimitProperties.Policy;
import com.github.sajjaadalipour.ratelimit.conf.properties.RateLimitProperties.Policy.Route;
import com.github.sajjaadalipour.ratelimit.http.CachedBodyHttpServletRequest;
import org.springframework.boot.web.servlet.filter.OrderedFilter;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.annotation.Nonnull;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

/**
 * A servlet filter to filtering requests to handle rate limiting.
 *
 * @author Sajjad Alipour
 */
public class RateLimitFilter extends OncePerRequestFilter implements OrderedFilter {

    /**
     * Encapsulates the rate limit properties.
     */
    private final RateLimitProperties rateLimitProperties;

    /**
     * Used to rate limiting.
     */
    private final RateLimiter rateLimiter;

    /**
     * Provides a map of key generators.
     */
    private final Map<String, RateLimitKeyGenerator> keyGenerators;

    /**
     * A utility class to path matching.
     */
    private final PathMatcher pathMatcher = new AntPathMatcher();

    /**
     * Used to handle too many request error.
     */
    private final TooManyRequestErrorHandler tooManyRequestErrorHandler;

    private final Map<String, List<Policy>> mapOfMatchedPolicies = new HashMap<>();

    public RateLimitFilter(
            RateLimitProperties rateLimitProperties,
            RateLimiter rateLimiter,
            Map<String, RateLimitKeyGenerator> keyGenerators,
            TooManyRequestErrorHandler tooManyRequestErrorHandler) {
        this.rateLimitProperties = rateLimitProperties;
        this.rateLimiter = rateLimiter;
        this.keyGenerators = keyGenerators;
        this.tooManyRequestErrorHandler = tooManyRequestErrorHandler;
    }

    /**
     * First for all, get matched policies from the {@code httpServletRequest} by http method and request uri,
     * iterates on matched policies then, first get the policy`s key generator to generate an identity key,
     * now inits a {@link RatePolicy} and pass to rate limiter to consume, if after consuming the rate result exceeded
     * return too many request error.
     *
     * @param httpServletRequest  The request to process.
     * @param httpServletResponse The response associated with the request.
     * @param filterChain         Provides access to the next filter in the chain for this
     *                            filter to pass the request and response to for further
     *                            processing.
     * @throws ServletException If the processing fails for any other reason.
     * @throws IOException      If an I/O error occurs during this filter's processing of the request.
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest httpServletRequest,
            @Nonnull HttpServletResponse httpServletResponse,
            @Nonnull FilterChain filterChain) throws ServletException, IOException {
        CachedBodyHttpServletRequest cachedBodyHttpServletRequest = new CachedBodyHttpServletRequest(httpServletRequest);
        List<Policy> matchedPolicies = getMatchedPolicies(httpServletRequest.getRequestURI(), httpServletRequest.getMethod());

        boolean doFilterChain = true;

        for (Policy policy : matchedPolicies) {
            final RateLimitKeyGenerator rateLimitKeyGenerator = keyGenerators.get(policy.getKeyGenerator());
            final String generatedKey = rateLimitKeyGenerator.generateKey(cachedBodyHttpServletRequest, policy);
            final RatePolicy ratePolicy = new RatePolicy(
                    generatedKey,
                    policy.getDuration(),
                    policy.getCount(),
                    (policy.getBlock() != null) ? policy.getBlock().getDuration() : null);

            Rate rate = rateLimiter.consume(ratePolicy);

            if (rate.isExceed() || rate.isBlocked()) {
                tooManyRequestErrorHandler.handle(httpServletResponse, rate);
                doFilterChain = false;
                break;
            }
        }

        if (doFilterChain) {
            filterChain.doFilter(cachedBodyHttpServletRequest, httpServletResponse);
        }
    }

    @Override
    public int getOrder() {
        return rateLimitProperties.getFilterOrder();
    }

    /**
     * This method get policies of a request according to it's uri and method.
     * The request uri must not be include in Exclude Routes of that policy
     * and between multiple policy with identical duration, the policy
     * with minimum duration is selected. eventually between all policy
     * ordering is done.
     *
     * @param uri    The request uri.
     * @param method The request method type.
     * @return A list of policies.
     */
    private List<Policy> getMatchedPolicies(String uri, String method) {
        List<Policy> policies = Optional.ofNullable(mapOfMatchedPolicies.get(uri + method)).orElse(new ArrayList<>());
        if (!policies.isEmpty()) return policies;

        rateLimitProperties.getPolicies()
                .stream()
                .filter(policy ->
                        isNoneMatchPolicyExcludeRoutesWithGivenRequestUriAndMethod(uri, method, policy) &&
                                isAnyMatchPolicyRoutesWithGivenRequestUriAndMethod(uri, method, policy)
                ).collect(groupingBy(policy -> policy.getDuration().toMillis()))
                .forEach((millisecond, policyList) ->
                        policyList
                                .stream()
                                .min(comparing(Policy::getCount))
                                .ifPresent(policies::add)

                );

        List<Policy> sortedPolicies = policies.stream().sorted(comparing(Policy::getDuration)).collect(toList());

        mapOfMatchedPolicies.put(uri + method, policies);

        return sortedPolicies;
    }

    private boolean isNoneMatchPolicyExcludeRoutesWithGivenRequestUriAndMethod(String uri, String method, Policy policy) {
        return policy.getExcludeRoutes().stream()
                .noneMatch(excludeRoute -> isMatchRouteWithGivenRequestUriAndMethod(uri, method, excludeRoute));
    }

    private boolean isAnyMatchPolicyRoutesWithGivenRequestUriAndMethod(String uri, String method, Policy policy) {
        return policy.getRoutes()
                .stream()
                .anyMatch(route -> isMatchRouteWithGivenRequestUriAndMethod(uri, method, route));
    }

    private boolean isMatchRouteWithGivenRequestUriAndMethod(String uri, String method, Route route) {
        if (route.getMethod() == null) {
            return pathMatcher.match(route.getUri(), uri);
        }
        return pathMatcher.match(route.getUri(), uri) && route.getMethod().name().equals(method);
    }
}
