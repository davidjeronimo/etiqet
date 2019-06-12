package com.neueda.etiqet.transport.jms;

import com.neueda.etiqet.core.client.delegate.ClientDelegate;
import com.neueda.etiqet.core.common.exceptions.EtiqetException;
import com.neueda.etiqet.core.common.exceptions.EtiqetRuntimeException;
import com.neueda.etiqet.core.message.cdr.Cdr;
import com.neueda.etiqet.core.transport.Codec;
import com.neueda.etiqet.core.transport.Transport;
import com.neueda.etiqet.core.transport.TransportDelegate;
import com.neueda.etiqet.transport.jms.config.ConstructorArgument;
import com.neueda.etiqet.transport.jms.config.JmsConfig;
import com.neueda.etiqet.transport.jms.config.JmsConfigExtractor;
import com.neueda.etiqet.transport.jms.config.SetterArgument;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Class used to interact with a jms bus
 */
public class JmsTransport implements Transport {

    private final static Logger logger = LoggerFactory.getLogger(JmsTransport.class);

    private ConnectionFactory connectionFactory;
    private Connection connection;
    private Session session;
    private Codec<Cdr, String> codec;
    private String defaultTopic;
    private ClientDelegate delegate;
    private TransportDelegate<String, Cdr> transDel;

    /**
     * Instantiates a Jms connection factory and determines the default topic to publish messages to
     *
     * @param configPath Path to the jms configuration
     * @throws EtiqetException when we're unable to read the configuration file
     */
    @Override
    public void init(String configPath) throws EtiqetException {
        JmsConfigExtractor jmsConfigExtractor = new JmsConfigExtractor();
        JmsConfig configuration = jmsConfigExtractor.retrieveConfiguration(configPath);
        connectionFactory = createConnectionFactory(configuration);
        defaultTopic = configuration.getDefaultTopic();
    }

    private ConnectionFactory createConnectionFactory(final JmsConfig configuration) throws EtiqetException {
        List<ConstructorArgument> constructorArguments = configuration.getConstructorArgs();
        final Class[] argumentClasses = constructorArguments.stream()
            .map(arg -> arg.getArgumentType().getClazz())
            .toArray(Class[]::new);
        final Object[] argumentValues = constructorArguments.stream()
            .map(ConstructorArgument::getValue)
            .toArray(Object[]::new);

        try {
            final Class<?> constructorClass = configuration.getImplementation();
            final ConnectionFactory cf = (ConnectionFactory) constructorClass.getConstructor(argumentClasses).newInstance(argumentValues);
            configuration.getSetterArgs().forEach(
                setterArgument -> setArgument(setterArgument, constructorClass, cf)
            );
            return cf;
        } catch (ReflectiveOperationException e) {
            throw new EtiqetException(e.getMessage());
        }

    }

    private void setArgument(final SetterArgument setterArgument, final Class<?> clazz, final ConnectionFactory connectionFactory) {
        try {
            Method method = clazz.getDeclaredMethod("set" + StringUtils.capitalize(setterArgument.getName()), setterArgument.getArgumentType().getClazz());
            method.invoke(connectionFactory, setterArgument.getValue());
        } catch (ReflectiveOperationException e) {
            throw new EtiqetRuntimeException("Invalid setter property for connection factory with name " + setterArgument.getName());
        }
    }



    /**
     * Starts a connection to the configured Jms bus
     *
     * @throws EtiqetException when unable to create a connection or session on the Jms bus
     */
    @Override
    public void start() throws EtiqetException {
        try {
            connection = connectionFactory.createConnection();
            connection.start();
            session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
        } catch (JMSException e) {
            throw new EtiqetException("Couldn't create Jms connection", e);
        }
    }

    /**
     * Stops the Jms bus connection and session
     */
    @Override
    public void stop() {
        if (session != null) {
            try {
                session.close();
            } catch (JMSException e) {
                logger.error("Couldn't safely stop Jms session", e);
            } finally {
                session = null;
            }
        }

        if (connection != null) {
            try {
                connection.stop();
            } catch (JMSException e) {
                logger.error("Couldn't safely stop Jms connection", e);
            } finally {
                connection = null;
            }
        }
    }

    /**
     * Sends a message to the Jms bus on the default topic provided in the configuration file
     *
     * @param msg message to be sent
     * @throws EtiqetException When an error occurs sending the message
     */
    @Override
    public void send(Cdr msg) throws EtiqetException {
        send(msg, getDefaultSessionId());
    }

    /**
     * Sends a message to the Jms bus on the default topic provided
     *
     * @param msg       message to be sent
     * @param topicName String containing the topic
     * @throws EtiqetException When an error occurs sending the message
     */
    @Override
    public void send(Cdr msg, String topicName) throws EtiqetException {
        if (StringUtils.isEmpty(topicName)) {
            logger.info("Empty topic name passed for sending to Jms, using default topic from config: {}", defaultTopic);
            topicName = getDefaultSessionId();
        }
        if (StringUtils.isEmpty(topicName)) {
            throw new EtiqetException("Unable to send message without a defined topic");
        }
        try {
            MessageProducer producer = session.createProducer(session.createTopic(topicName));
            producer.send(session.createTextMessage(codec.encode(msg)));
        } catch (JMSException e) {
            logger.error("Exception sending message to Jms bus.", e);
            throw new EtiqetException(e);
        }
    }

    /**
     * @return whether a connection is established to the jms bus
     */
    @Override
    public boolean isLoggedOn() {
        return connection != null;
    }

    /**
     * @return the default topic to send messages to
     */
    @Override
    public String getDefaultSessionId() {
        return defaultTopic;
    }

    /**
     * @param transDel the transport delegate class
     */
    @Override
    public void setTransportDelegate(TransportDelegate<String, Cdr> transDel) {
        this.transDel = transDel;
    }

    /**
     * @param c the codec used by the transport.
     */
    @Override
    @SuppressWarnings("unchecked")
    public void setCodec(Codec c) {
        this.codec = c;
    }

    /**
     * @return the codec used by the transport.
     */
    @Override
    public Codec getCodec() {
        return codec;
    }

    /**
     * @param delegate the first client delegate of a chain to use (if any)
     */
    @Override
    public void setDelegate(ClientDelegate delegate) {
        this.delegate = delegate;
    }

    /**
     * @return the first client delegate of a chain to be used (if any)
     */
    @Override
    public ClientDelegate getDelegate() {
        return delegate;
    }

    ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    void setConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    Connection getConnection() {
        return connection;
    }

    void setConnection(Connection connection) {
        this.connection = connection;
    }

    Session getSession() {
        return session;
    }

    void setSession(Session session) {
        this.session = session;
    }

    void setDefaultTopic(String defaultTopic) {
        this.defaultTopic = defaultTopic;
    }

}
