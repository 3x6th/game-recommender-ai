package ru.perevalov.gamerecommenderai.config;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Operators;
import reactor.util.context.Context;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Configuration
public class ReactorMdcConfiguration {

    private static final String MDC_REACTOR_HOOK_KEY = "reactor-mdc";

    @Value("${requestid.logging.param}")
    private String requestIdLoggingParam;

    @PostConstruct
    public void setupHooks() {
        Hooks.onEachOperator(MDC_REACTOR_HOOK_KEY, Operators.lift((scannable, subscriber) ->
                new MdcContextLifter<>(subscriber, requestIdLoggingParam)));
    }

    @PreDestroy
    public void cleanupHooks() {
        Hooks.resetOnEachOperator(MDC_REACTOR_HOOK_KEY);
    }

    private static final class MdcContextLifter<T> implements CoreSubscriber<T> {

        private final CoreSubscriber<? super T> delegate;
        private final String requestIdMdcKey;

        private MdcContextLifter(CoreSubscriber<? super T> delegate, String requestIdMdcKey) {
            this.delegate = delegate;
            this.requestIdMdcKey = requestIdMdcKey;
        }

        @Override
        public Context currentContext() {
            return delegate.currentContext();
        }

        @Override
        public void onSubscribe(org.reactivestreams.Subscription subscription) {
            withMdc(() -> delegate.onSubscribe(subscription));
        }

        @Override
        public void onNext(T value) {
            withMdc(() -> delegate.onNext(value));
        }

        @Override
        public void onError(Throwable throwable) {
            withMdc(() -> delegate.onError(throwable));
        }

        @Override
        public void onComplete() {
            withMdc(delegate::onComplete);
        }

        private void withMdc(Runnable runnable) {
            Context context = delegate.currentContext();
            String previous = MDC.get(requestIdMdcKey);

            try {
                if (context.hasKey(requestIdMdcKey)) {
                    MDC.put(requestIdMdcKey, context.get(requestIdMdcKey));
                } else {
                    MDC.remove(requestIdMdcKey);
                }
                runnable.run();
            } finally {
                if (previous == null) {
                    MDC.remove(requestIdMdcKey);
                } else {
                    MDC.put(requestIdMdcKey, previous);
                }
            }
        }
    }
}
