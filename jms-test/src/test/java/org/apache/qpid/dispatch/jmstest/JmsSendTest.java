package org.apache.qpid.dispatch.jmstest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

public class JmsSendTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(JmsSendTest.class);

    private ConnectionFactory requestConnectionFactory;
    private Destination requestQueue;

    @BeforeEach
    public void setup() throws NamingException {
        // The configuration for the Qpid InitialContextFactory has been supplied in
        // a jndi.properties file in the classpath, which results in it being picked
        // up automatically by the InitialContext constructor.
        Context context = new InitialContext();

        this.requestConnectionFactory = (ConnectionFactory) context.lookup("dispatcher1FactoryLookup");
        this.requestQueue = (Destination) context.lookup("requests");
    }

    @Test
    public void send_message_to_non_existing_queue() throws Exception {
        try (Connection connection = requestConnectionFactory.createConnection()) {
            connection.setExceptionListener(new MyExceptionListener());
            connection.start();

            try (Session session1 = connection.createSession()) {
                sendRequest(session1, requestQueue);
            }
        }
    }


    private void sendRequest(Session session, Destination destination) throws JMSException {
        try (MessageProducer requestProducer = session.createProducer(destination)) {
            TextMessage message = session.createTextMessage("Hello world!");
            message.setJMSExpiration(10000l);
            requestProducer.send(message);
            LOGGER.info(String.format("Sent request: %s", destination));
            logDetails(message);
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
