package org.apache.qpid.dispatch.jmstest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
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
import java.util.concurrent.atomic.AtomicReference;

public class RequestResponseReplyToTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestResponseReplyToTest.class);

    private ConnectionFactory connectionFactory;
    private Destination requestQueue;

    @BeforeEach
    public void setup() throws NamingException {
        // The configuration for the Qpid InitialContextFactory has been supplied in
        // a jndi.properties file in the classpath, which results in it being picked
        // up automatically by the InitialContext constructor.
        Context context = new InitialContext();

        this.connectionFactory = (ConnectionFactory) context.lookup("dispatcherFactoryLookup");
        this.requestQueue = (Destination) context.lookup("requests");
    }

    @Test
    public void request_response_with_reply_to_listener() throws Exception {
        try (Connection connection = connectionFactory.createConnection()) {
            connection.setExceptionListener(new MyExceptionListener());
            connection.start();

            try (Session session1 = connection.createSession()) {
                TemporaryQueue responseQueue = session1.createTemporaryQueue();
                LOGGER.info("Temporary Response Queue: " + responseQueue);

                try (MessageConsumer responseConsumer = session1.createConsumer(responseQueue)) {
                    AtomicReference<Message> receivedResponse = new AtomicReference<>();
                    responseConsumer.setMessageListener(receivedResponse::set);
                    LOGGER.info("Temporary Response Queue Consumer Created: " + responseQueue);

                    new Thread(this::responseHandler, "Response Handler").start();
                    Thread.sleep(2000);

                    sendRequest(session1, responseQueue);

                    Thread.sleep(10000);
                    if (receivedResponse.get() == null) {
                        throw new RuntimeException("no message received!");
                    }
                    LOGGER.info("Received response:");
                    logDetails(receivedResponse.get());
                }
            }
        }
    }

    @Test
    public void request_response_with_reply_to_receive() throws Exception {
        try (Connection connection = connectionFactory.createConnection()) {
            connection.setExceptionListener(new MyExceptionListener());
            connection.start();

            try (Session session1 = connection.createSession()) {
                TemporaryQueue responseQueue = session1.createTemporaryQueue();
                LOGGER.info("Temporary Response Queue: " + responseQueue);

                try (MessageConsumer responseConsumer = session1.createConsumer(responseQueue)) {
                    LOGGER.info("Temporary Response Queue Consumer Created: " + responseQueue);

                    new Thread(this::responseHandler, "Response Handler").start();
                    Thread.sleep(2000);

                    sendRequest(session1, responseQueue);

                    Message receivedResponse = responseConsumer.receive(10000);
                    if (receivedResponse == null) {
                        throw new RuntimeException("no message received!");
                    }
                    LOGGER.info("Received response:");
                    logDetails(receivedResponse);
                }
            }
        }
    }

    private void sendRequest(Session session, TemporaryQueue responseQueue) throws JMSException {
        try (MessageProducer requestProducer = session.createProducer(requestQueue)) {
            TextMessage message = session.createTextMessage("Hello world!");
            message.setJMSReplyTo(responseQueue);
            message.setJMSExpiration(10000l);
            requestProducer.send(message);
            LOGGER.info(String.format("Sent request: %s", requestQueue));
            logDetails(message);
        }
    }

    private void responseHandler() {
        try {
            try (Connection connection = connectionFactory.createConnection()) {
                connection.setExceptionListener(new MyExceptionListener());
                connection.start();
                try (Session session2 = connection.createSession()) {
                    try (MessageConsumer requestConsumer = session2.createConsumer(requestQueue)) {
                        LOGGER.info("Request Queue Consumer Created: " + requestQueue);
                        TextMessage receivedMessage = (TextMessage) requestConsumer.receive(20000);
                        if (receivedMessage == null) {
                            throw new RuntimeException("no message received");
                        }

                        LOGGER.info("Received request:");
                        logDetails(receivedMessage);

                        Destination replyTo = receivedMessage.getJMSReplyTo();
                        // TEST by setting response queue directly
                        // Destination replyTo = responseQueue;
                        try (MessageProducer messageProducer = session2.createProducer(replyTo)) {
                            LOGGER.info("Temporary Response Queue Producer Created: " + replyTo);
                            TextMessage message = session2.createTextMessage("Hello world response!");
//                            message.setJMSDeliveryMode(DeliveryMode.NON_PERSISTENT);

                            LOGGER.info(String.format("Sending response: %s", replyTo));
                            logDetails(message);
                            messageProducer.send(message);

                            LOGGER.info("Sent response:");
                            logDetails(message);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("failed to handle response", e);
        }
    }

    private void logDetails(Message receivedMessage) throws JMSException {
        LOGGER.info(String.format("\tMessageID:%s", receivedMessage.getJMSMessageID()));
        LOGGER.info(String.format("\tCorrelationID:%s", receivedMessage.getJMSCorrelationID()));
        LOGGER.info(String.format("\tTimestamp:%s", receivedMessage.getJMSTimestamp()));
        LOGGER.info(String.format("\tExpiration:%s", receivedMessage.getJMSExpiration()));
        LOGGER.info(String.format("\tDeliveryTime:%s", receivedMessage.getJMSDeliveryTime()));
        LOGGER.info(String.format("\tReply-To:%s", receivedMessage.getJMSReplyTo()));
        LOGGER.info(String.format("\tBody:%s", receivedMessage.getBody(String.class)));
    }

    private static class MyExceptionListener implements ExceptionListener {
        @Override
        public void onException(JMSException exception) {
            LOGGER.error("Connection ExceptionListener fired, exiting.", exception);
        }
    }

}
