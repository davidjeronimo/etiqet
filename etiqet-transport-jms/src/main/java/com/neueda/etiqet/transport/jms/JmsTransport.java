package com.neueda.etiqet.transport.jms;

import com.neueda.etiqet.core.client.delegate.ClientDelegate;
import com.neueda.etiqet.core.common.exceptions.EtiqetException;
import com.neueda.etiqet.core.common.exceptions.EtiqetRuntimeException;
import com.neueda.etiqet.core.message.cdr.Cdr;
import com.neueda.etiqet.core.message.config.AbstractDictionary;
import com.neueda.etiqet.core.transport.BrokerTransport;
import com.neueda.etiqet.core.transport.Codec;
import com.neueda.etiqet.core.transport.TransportDelegate;
import com.neueda.etiqet.core.transport.delegate.BinaryMessageConverterDelegate;
import com.neueda.etiqet.transport.jms.config.JmsConfigExtractor;
import com.neueda.etiqet.transport.jms.config.JmsConfigXmlParser;
import com.neueda.etiqet.transport.jms.config.model.ConstructorArgument;
import com.neueda.etiqet.transport.jms.config.model.JmsConfig;
import com.neueda.etiqet.transport.jms.config.model.SetterArgument;
import com.neueda.etiqet.transport.jms.routes.JmsRouteBuilder;
import org.apache.camel.*;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.neueda.etiqet.transport.jms.DestinationType.QUEUE;
import static com.neueda.etiqet.transport.jms.DestinationType.TOPIC;
import static com.neueda.etiqet.transport.jms.routes.JmsRouteBuilder.DESTINATION_NAME;

/**
 * Class used to interact with a jms bus
 */
public class JmsTransport implements BrokerTransport {

    private final static Logger logger = LoggerFactory.getLogger(JmsTransport.class);

    private ConnectionFactory connectionFactory;
    //private Connection connection;
    //private Session session;
    private Codec<Cdr, ?> codec;
    private AbstractDictionary dictionary;
    private String defaultTopic;
    private ClientDelegate delegate;
    private BinaryMessageConverterDelegate binaryMessageConverterDelegate;
    private JmsConfig configuration;
    private JmsRouteBuilder jmsRouteBuilder;
    private CamelContext camelContext;
    private ProducerTemplate producerTemplate;

    /**
     * Instantiates a Jms connection factory and determines the default topic to publish messages to
     *
     * @param configPath Path to the jms configuration
     * @throws EtiqetException when we're unable to read the configuration file
     */
    @Override
    public void init(String configPath) throws EtiqetException {
        JmsConfigExtractor jmsConfigExtractor = new JmsConfigExtractor(new JmsConfigXmlParser());
        configuration = jmsConfigExtractor.retrieveConfiguration(configPath);
        binaryMessageConverterDelegate = instantiateBinaryMessageConverterDelegate(configuration);
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
            Method method = clazz.getMethod("set" + StringUtils.capitalize(setterArgument.getName()), setterArgument.getArgumentType().getClazz());
            method.invoke(connectionFactory, setterArgument.getValue());
        } catch (ReflectiveOperationException e) {
            throw new EtiqetRuntimeException("Invalid setter property for connection factory with name " + setterArgument.getName());
        }
    }

    private BinaryMessageConverterDelegate instantiateBinaryMessageConverterDelegate(JmsConfig config) throws EtiqetException {
        Optional<Class> delegateClass = config.getBinaryMessageConverterDelegateClass();
        if (delegateClass.isPresent()) {
            try {
                BinaryMessageConverterDelegate delegate = (BinaryMessageConverterDelegate) delegateClass.get().newInstance();
                delegate.setDictionary(dictionary);
                return delegate;
            } catch (ReflectiveOperationException e) {
                throw new EtiqetException("Unable to instantiate BinaryMessageConverterDelegate " + delegateClass.get().getName());
            }
        } else {
            return null;
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
            camelContext = new DefaultCamelContext();
            camelContext.addComponent("jms", JmsComponent.jmsComponentAutoAcknowledge(connectionFactory));
            jmsRouteBuilder = new JmsRouteBuilder(codec, binaryMessageConverterDelegate);
            camelContext.addRoutes(jmsRouteBuilder);
            camelContext.start();
            producerTemplate = camelContext.createProducerTemplate();
        } catch (Exception e) {
            throw new EtiqetException (e);
        }

       /* try {
            connection = connectionFactory.createConnection();
            connection.start();
            session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        } catch (JMSException e) {
            throw new EtiqetException("Couldn't create Jms connection", e);
        }*/
    }

    /**
     * Stops the Jms bus connection and session
     */
    @Override
    public void stop() {
        try {
            camelContext.stop();
        } catch (Exception e) {}
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
        sendToTopic(msg, Optional.ofNullable(topicName));
    }

    /**
     * @return whether a connection is established to the jms bus
     */
    @Override
    public boolean isLoggedOn() {
        return true;
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

    @Override
    public void subscribeToTopic(Optional<String> topicName, final Consumer<Cdr> cdrListener) throws EtiqetException {
        subscribeToDestination(TOPIC, topicName.orElse(defaultTopic), cdrListener);
    }

    @Override
    public void subscribeToQueue(String queueName, Consumer<Cdr> cdrListener) throws EtiqetException {
        subscribeToDestination(QUEUE, queueName, cdrListener);
    }


    public void subscribeToDestination(DestinationType destinationType, final String destinationName, final Consumer<Cdr> cdrListener) throws EtiqetException {
        try {
            camelContext.addRouteDefinition(
                jmsRouteBuilder.createListenerRouteDefinition(destinationType, destinationName, cdrListener)
            );
        } catch (Exception e) {
            throw new EtiqetException(e);
        }
    }

    @Override
    public void sendToTopic(Cdr cdr, Optional<String> maybeTopicName) throws EtiqetException {
        final String topicName = maybeTopicName.orElseGet(() -> {
            logger.info("Empty topic name passed for sending to Jms, using default topic from config: {}", defaultTopic);
            return defaultTopic;
        });

        Map<String, Object> params = new HashMap<>();
        params.put(DESTINATION_NAME, topicName);
        producerTemplate.sendBodyAndHeaders("direct:sendToTopic", cdr, params);
       }

    @Override
    public void sendToQueue(Cdr cdr, String queueName) throws EtiqetException {
        if (StringUtils.isEmpty(queueName)) {
            throw new EtiqetException("Unable to send message without a defined queue");
        }
        Map<String, Object> params = new HashMap<>();
        params.put(DESTINATION_NAME, queueName);
        producerTemplate.sendBodyAndHeaders("vm:sendToQueue", cdr, params);
    }


    @Override
    public Cdr subscribeAndConsumeFromTopic(final Optional<String> topicName, final Duration timeout) throws EtiqetException {
        return subscribeAndConsumeFromDestination("topic", topicName.orElse(defaultTopic), timeout);
    }

    @Override
    public Cdr subscribeAndConsumeFromQueue(final String queueName, final Duration timeout) throws EtiqetException {
        return subscribeAndConsumeFromDestination("queue", queueName, timeout);
    }

    public Cdr subscribeAndConsumeFromDestination(final String destinationName, final String destinationType, final Duration timeout) throws EtiqetException {
        ConsumerTemplate consumerTemplate = camelContext.createConsumerTemplate();
        CompletableFuture<Exchange> eventualExchange = CompletableFuture.supplyAsync(
            () -> consumerTemplate.receive("vm:" + destinationType + ":" + destinationName)
        );
        try {
            Exchange exchange = eventualExchange.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return exchange.getIn().getBody(Cdr.class);
        } catch (Exception e) {
            throw new EtiqetException(e);
        }
    }

    ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    void setConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    /*Connection getConnection() {
        return null;
    }

    void setConnection(Connection connection) {
        this.connection = connection;
    }

    Session getSession() {
        return session;
    }

    void setSession(Session session) {
        this.session = session;
    }*/

    void setDefaultTopic(String defaultTopic) {
        this.defaultTopic = defaultTopic;
    }

    @Override
    public void setDictionary(AbstractDictionary dictionary) {
        this.dictionary = dictionary;
    }

    public void setBinaryMessageConverterDelegate(BinaryMessageConverterDelegate binaryMessageConverterDelegate) {
        this.binaryMessageConverterDelegate = binaryMessageConverterDelegate;
    }
}
