package com.slack.api.methods.impl;

import com.slack.api.methods.Methods;
import com.slack.api.methods.MethodsConfig;
import com.slack.api.methods.MethodsCustomRateLimitResolver;
import com.slack.api.methods.MethodsRateLimits;
import com.slack.api.rate_limits.RateLimiter;
import com.slack.api.rate_limits.WaitTime;
import com.slack.api.rate_limits.WaitTimeCalculator;
import com.slack.api.rate_limits.metrics.MetricsDatastore;
import com.slack.api.rate_limits.metrics.RequestPace;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

import static com.slack.api.methods.MethodsRateLimitTier.SpecialTier_assistant_threads_setStatus;
import static com.slack.api.methods.MethodsRateLimitTier.SpecialTier_chat_postMessage;

@Slf4j
public class AsyncMethodsRateLimiter implements RateLimiter {

    private final MetricsDatastore metricsDatastore;
    private final MethodsCustomRateLimitResolver customRateLimitResolver;
    private final WaitTimeCalculator waitTimeCalculator;
    private final String executorName;

    public MetricsDatastore getMetricsDatastore() {
        return metricsDatastore;
    }

    public AsyncMethodsRateLimiter(MethodsConfig config) {
        this.metricsDatastore = config.getMetricsDatastore();
        this.customRateLimitResolver = config.getCustomRateLimitResolver();
        this.waitTimeCalculator = new MethodsWaitTimeCalculator(config);
        this.executorName = config.getExecutorName();
    }

    @Override
    public WaitTime acquireWaitTime(String teamId, String methodName) {
        Optional<Long> rateLimitedEpochMillis = waitTimeCalculator
                .getRateLimitedMethodRetryEpochMillis(executorName, teamId, methodName);
        if (rateLimitedEpochMillis.isPresent()) {
            long millisToWait = rateLimitedEpochMillis.get() - System.currentTimeMillis();
            return new WaitTime(millisToWait, RequestPace.RateLimited);
        }
        return waitTimeCalculator.calculateWaitTime(
                teamId,
                methodName,
                getAllowedRequestsPerMinute(teamId, methodName)
        );
    }

    public int getAllowedRequestsPerMinute(String teamId, String methodName) {
        Optional<Integer> custom = customRateLimitResolver.getCustomAllowedRequestsPerMinute(teamId, methodName);
        if (custom.isPresent()) {
            return custom.get();
        }
        return waitTimeCalculator.getAllowedRequestsPerMinute(MethodsRateLimits.lookupRateLimitTier(methodName));
    }

    public int getAllowedRequestsForChatPostMessagePerMinute(String teamId, String channel) {
        Optional<Integer> custom = customRateLimitResolver.getCustomAllowedRequestsForChatPostMessagePerMinute(teamId, channel);
        if (custom.isPresent()) {
            return custom.get();
        }
        return waitTimeCalculator.getAllowedRequestsPerMinute(SpecialTier_chat_postMessage);
    }

    @Override
    public WaitTime acquireWaitTimeForChatPostMessage(String teamId, String channel) {
        // See MethodsClientImpl#buildMethodNameAndSuffix() for the consistency of this logic
        String methodName = Methods.CHAT_POST_MESSAGE + "_" + channel;
        Optional<Long> rateLimitedEpochMillis = waitTimeCalculator
                .getRateLimitedMethodRetryEpochMillis(executorName, teamId, methodName);
        if (rateLimitedEpochMillis.isPresent()) {
            long millisToWait = rateLimitedEpochMillis.get() - System.currentTimeMillis();
            return new WaitTime(millisToWait, RequestPace.RateLimited);
        }
        return waitTimeCalculator.calculateWaitTimeForChatPostMessage(
                teamId,
                channel,
                getAllowedRequestsForChatPostMessagePerMinute(teamId, channel)
        );
    }

    public int getAllowedRequestsForAssistantThreadsSetStatusPerMinute(String teamId, String channel) {
        Optional<Integer> custom = customRateLimitResolver.getCustomAllowedRequestsForAssistantThreadsSetStatusPerMinute(teamId, channel);
        if (custom.isPresent()) {
            return custom.get();
        }
        return waitTimeCalculator.getAllowedRequestsPerMinute(SpecialTier_assistant_threads_setStatus);
    }

    @Override
    public WaitTime acquireWaitTimeForAssistantThreadsSetStatus(String teamId, String channel) {
        // See MethodsClientImpl#buildMethodNameAndSuffix() for the consistency of this logic
        String methodName = Methods.ASSISTANT_THREADS_SET_STATUS + "_" + channel;
        Optional<Long> rateLimitedEpochMillis = waitTimeCalculator
                .getRateLimitedMethodRetryEpochMillis(executorName, teamId, methodName);
        if (rateLimitedEpochMillis.isPresent()) {
            long millisToWait = rateLimitedEpochMillis.get() - System.currentTimeMillis();
            return new WaitTime(millisToWait, RequestPace.RateLimited);
        }
        return waitTimeCalculator.calculateWaitTimeForAssistantThreadsSetStatus(
                teamId,
                channel,
                getAllowedRequestsForAssistantThreadsSetStatusPerMinute(teamId, channel)
        );
    }

    public static class MethodsWaitTimeCalculator extends WaitTimeCalculator {
        private final MethodsConfig config;

        public MethodsWaitTimeCalculator(MethodsConfig config) {
            this.config = config;
        }

        @Override
        public Optional<Long> getRateLimitedMethodRetryEpochMillis(String executorName, String teamId, String key) {
            return Optional.ofNullable(config.getMetricsDatastore().getRateLimitedMethodRetryEpochMillis(
                    executorName, teamId, key
            ));
        }

        @Override
        public Integer getNumberOfNodes() {
            return config.getMetricsDatastore().getNumberOfNodes();
        }

        @Override
        public String getExecutorName() {
            return config.getExecutorName();
        }

        @Override
        public com.slack.api.rate_limits.metrics.LastMinuteRequests getLastMinuteRequests(
                String executorName, String teamId, String key) {
            return config.getMetricsDatastore().getLastMinuteRequests(executorName, teamId, key);
        }
    }
}
