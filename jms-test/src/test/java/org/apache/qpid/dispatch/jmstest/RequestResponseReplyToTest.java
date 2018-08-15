package org.apache.qpid.dispatch.jmstest;

import org.junit.jupiter.api.Test;
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

public class RequestResponseReplyToTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestResponseReplyToTest.class);

    @Test
    public void send_request_with_reply_to() throws Exception {
        // The configuration for the Qpid InitialContextFactory has been supplied in
        // a jndi.properties file in the classpath, which results in it being picked
        // up automatically by the InitialContext constructor.
        Context context = new InitialContext();

        ConnectionFactory factory = (ConnectionFactory) context.lookup("dispatcherFactoryLookup");
        Destination queue = (Destination) context.lookup("requests");

        try (Connection connection = factory.createConnection()) {
            connection.setExceptionListener(new MyExceptionListener());
            connection.start();

            try (Session session1 = connection.createSession()) {
                TemporaryQueue responseQueue = session1.createTemporaryQueue();
                LOGGER.info("Temporary Response Queue: " + responseQueue);

                try (MessageConsumer responseConsumer = session1.createConsumer(responseQueue)) {
                    LOGGER.info("Temporary Response Queue Consumer Created: " + responseQueue);

                    try (MessageProducer requestProducer = session1.createProducer(queue)) {
                        TextMessage message = session1.createTextMessage("Hello world!");
                        message.setJMSReplyTo(responseQueue);
                        requestProducer.send(message);
                        LOGGER.info(String.format("Sent request: %s", queue));
                        logDetails(message);
                    }

                    try (Session session2 = connection.createSession()) {
                        try (MessageConsumer requestConsumer = session2.createConsumer(queue)) {
                            LOGGER.info("Request Queue Consumer Created: " + queue);
                            TextMessage message1 = (TextMessage) requestConsumer.receive(10000);
                            if (message1 == null) {
                                throw new RuntimeException("no message received");
                            }

                            LOGGER.info("Received request:");
                            logDetails(message1);

                            Destination replyTo = message1.getJMSReplyTo();
                            // Destination replyTo = responseQueue; // TEST by setting response queue directly
                            try (MessageProducer messageProducer = session2.createProducer(replyTo)) {
                                LOGGER.info("Temporary Response Queue Producer Created: " + replyTo);
                                TextMessage message = session2.createTextMessage("Hello world response!");

                                LOGGER.info(String.format("Sending response: %s", replyTo));
                                logDetails(message);
                                messageProducer.send(message);

                                LOGGER.info("Sent response:");
                                logDetails(message);
                            }
                        }

                    }

                    TextMessage receivedResponse = (TextMessage) responseConsumer.receive(10000);
                    if (receivedResponse == null) {
                        throw new RuntimeException("no message received!");
                    }
                    LOGGER.info("Received response:");
                    logDetails(receivedResponse);
                }
            }
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
