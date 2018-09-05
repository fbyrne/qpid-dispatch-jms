package org.apache.qpid.dispatch.jmstest;

import com.google.common.collect.Maps;
import io.jaegertracing.internal.JaegerTracer;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.contrib.jms.common.TracingMessageConsumer;
import io.opentracing.contrib.jms.common.TracingMessageUtils;
import io.opentracing.contrib.jms2.TracingMessageProducer;
import io.opentracing.contrib.metrics.micrometer.MicrometerMetricsReporter;
import io.opentracing.contrib.tracerresolver.TracerResolver;
import io.opentracing.log.Fields;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.HashMap;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


public class BenchmarkApplication {

    private static final Logger LOGGER = LoggerFactory.getLogger(BenchmarkApplication.class);
    private final io.opentracing.Tracer tracer;
    private ConnectionFactory publisherConnectionFactory;
    private ConnectionFactory subscriberConnectionFactory;
    private Destination requestQueue;

    private BenchmarkApplication(io.opentracing.Tracer tracer) {
        this.tracer = tracer;
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expecting one argument");
        }
        Integer iterations = Integer.parseInt(args[0]);

        JaegerTracer jaegerTracer = setupTracer();

        Tracer tracer = GlobalTracer.get();
        Span span = tracer
                .buildSpan("benchmark")
                .start();
        try (Scope scope = tracer.scopeManager()
                .activate(span, false)) {
            span.finish();
            new BenchmarkApplication(tracer).executeBenchmark(span);
        } catch (Exception ex) {
            traceError(span, ex);
        }

        jaegerTracer.close();
    }

    private static JaegerTracer setupTracer(){
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        Metrics.addRegistry(registry);

        MicrometerMetricsReporter reporter = MicrometerMetricsReporter.newMetricsReporter()
                .withName("Benchmark")
                .withConstLabel("span.kind", Tags.SPAN_KIND_CLIENT)
                .build();

        JaegerTracer jaegerTracer = (JaegerTracer) TracerResolver.resolveTracer();
        Tracer tracerWithMetrics = io.opentracing.contrib.metrics.Metrics.decorate(
                jaegerTracer,
                reporter);

        GlobalTracer.register(tracerWithMetrics);
        return jaegerTracer;
    }

    private static void traceError(Span span, Exception ex) {
        Tags.ERROR.set(span, true);
        HashMap<String, Object> map = Maps.newHashMap();
        map.put(Fields.EVENT, "error");
        map.put(Fields.ERROR_OBJECT, ex);
        map.put(Fields.MESSAGE, ex.getMessage());
        span.log(map);
    }

    public void sendRequest(Span parentSpan, Session session, Destination destination, Destination replyTo) {
        Span span = tracer
                .buildSpan("request").asChildOf(parentSpan)
                .start();
        try (Scope scope = tracer.scopeManager().activate(span, false)) {
            try (MessageProducer requestProducer = new TracingMessageProducer(session.createProducer(destination), tracer)) {
                TextMessage message = session.createTextMessage("Hello world!");
                message.setJMSReplyTo(replyTo);
                message.setJMSExpiration(2000l);
                requestProducer.send(message);
                if(LOGGER.isDebugEnabled()) {
                    LOGGER.info(String.format("Sent request: %s", destination));
                    logDetails(message);
                }
            }
        } catch (Exception ex) {
            traceError(span, ex);
        } finally {
            span.finish();
        }

    }

    private static void logDetails(Message receivedMessage) throws JMSException {
        LOGGER.info(String.format("\tMessageID:%s", receivedMessage.getJMSMessageID()));
        LOGGER.info(String.format("\tCorrelationID:%s", receivedMessage.getJMSCorrelationID()));
        LOGGER.info(String.format("\tTimestamp:%s", receivedMessage.getJMSTimestamp()));
        LOGGER.info(String.format("\tExpiration:%s", receivedMessage.getJMSExpiration()));
        LOGGER.info(String.format("\tDeliveryTime:%s", receivedMessage.getJMSDeliveryTime()));
        LOGGER.info(String.format("\tReply-To:%s", receivedMessage.getJMSReplyTo()));
        LOGGER.info(String.format("\tBody:%s", receivedMessage.getBody(String.class)));
    }

    private void executeBenchmark(Span benchmarkSpan) throws NamingException, JMSException {
        // The configuration for the Qpid InitialContextFactory has been supplied in
        // a jndi.properties file in the classpath, which results in it being picked
        // up automatically by the InitialContext constructor.
        Context context = new InitialContext();

        this.publisherConnectionFactory = (ConnectionFactory) context.lookup("dispatcher1FactoryLookup");
        this.subscriberConnectionFactory = (ConnectionFactory) context.lookup("dispatcher2FactoryLookup");
        this.requestQueue = (Destination) context.lookup("requests");

        try (Connection connection = publisherConnectionFactory.createConnection()) {
            connection.setExceptionListener(new MyExceptionListener());
            connection.start();

            try (RequestHandlerFactory requestHandlerFactory = new RequestHandlerFactory()){
                requestHandlerFactory.start();
                try (Session session1 = connection.createSession()) {
                    for (int operation = 0; operation < 1000; operation++) {
                        Span span = tracer.buildSpan("request")
                                .asChildOf(benchmarkSpan)
                                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_PRODUCER).start();
                        span.setTag("iteration", operation);
                        try (Scope scope = tracer.scopeManager()
                                .activate(span, false)) {
                            executeRequest(span, session1);
                        } catch (Exception ex) {
                            traceError(span, ex);
                        } finally {
                            span.finish();
                        }
                    }
                }
            }
        }
    }

    private void executeRequest(Span span, Session session1) throws JMSException, InterruptedException {
        TemporaryQueue responseQueue = session1.createTemporaryQueue();
        try {
            span.setTag("responseQueue", String.valueOf(responseQueue));

            LOGGER.info("Temporary Response Queue: " + responseQueue);
            try (MessageConsumer responseConsumer = new TracingMessageConsumer(session1.createConsumer(responseQueue), tracer)) {
                Exchanger<Message> exchanger = new Exchanger<>();
                responseConsumer.setMessageListener(x -> {
                    try {
                        exchanger.exchange(x);
                    } catch (InterruptedException e) {
                    }
                });
                LOGGER.info("Temporary Response Queue Consumer Created: " + responseQueue);

                sendRequest(span, session1, requestQueue, responseQueue);

                try {
                    Message response = exchanger.exchange(null, 20, TimeUnit.SECONDS);
                    if (response == null) {
                        throw new RuntimeException("no message received!");
                    }

                    if(LOGGER.isDebugEnabled()) {
                        LOGGER.info("Received response:");
                        logDetails(response);
                    }
                } catch (TimeoutException e) {
                    throw new RuntimeException("no message received!");
                }
            }
        } finally {
            responseQueue.delete();
        }
    }


    private class RequestHandlerFactory implements AutoCloseable{

        private final Connection connection;
        private final Session session;
        private TracingMessageConsumer requestConsumer;

        RequestHandlerFactory() throws JMSException {
            this.connection = subscriberConnectionFactory.createConnection();
            this.connection.setExceptionListener(new MyExceptionListener());
            this.connection.start();
            this.session = connection.createSession();
        }

        public void start() throws JMSException {
            this.requestConsumer = new TracingMessageConsumer(session.createConsumer(requestQueue), tracer);
            this.requestConsumer.setMessageListener(message -> {
                Scope followingScope = TracingMessageUtils.buildAndFinishChildSpan(message, tracer);
                Span span = tracer.buildSpan("response")
                        .asChildOf(followingScope.span())
                        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_PRODUCER).start();
                try (Scope scope = tracer.scopeManager()
                        .activate(span, false)) {
                    onRequestMessage(message);
                } catch (Exception ex) {
                    traceError(span, ex);
                } finally {
                    span.finish();
                }
            });
            LOGGER.info("Request Queue Consumer Created: " + requestQueue);
        }

        @Override
        public void close() {
            try {
                this.requestConsumer.close();
            } catch (JMSException e) {
            }
            try {
                this.session.close();
            } catch (JMSException e) {
            }
            try {
                this.connection.close();
            } catch (JMSException e) {
            }
        }

        private void onRequestMessage(Message receivedMessage) {
            try {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.info("Received request:");
                    logDetails(receivedMessage);
                }

                Destination replyTo = receivedMessage.getJMSReplyTo();
                try (TracingMessageProducer messageProducer = new TracingMessageProducer(session.createProducer(replyTo), tracer)) {
                    LOGGER.info("Temporary Response Queue Producer Created: " + replyTo);
                    TextMessage message = session.createTextMessage("Hello world response!");

                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.info(String.format("Sending response: %s", replyTo));
                        logDetails(message);
                    }
                    messageProducer.send(message);

                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Sent response:");
                        logDetails(message);
                    }
                }
            } catch (JMSException e) {
                LOGGER.error("unable to handle response", e);
            }
        }
    }

    private class MyExceptionListener implements ExceptionListener {
        @Override
        public void onException(JMSException ex) {
            LOGGER.error("Connection ExceptionListener fired, exiting.", ex);
            try(Scope scope = tracer.buildSpan("exception").startActive(true)) {
                Span span = scope.span();
                Tags.ERROR.set(span, true);
                HashMap<String, Object> map = Maps.newHashMap();
                map.put(Fields.EVENT, "error");
                map.put(Fields.ERROR_OBJECT, ex);
                map.put(Fields.MESSAGE, ex.getMessage());
                span.log(map);
            }
        }
    }

}
