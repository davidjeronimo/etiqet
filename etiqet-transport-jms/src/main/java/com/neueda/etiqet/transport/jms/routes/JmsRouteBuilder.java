package com.neueda.etiqet.transport.jms.routes;

import com.neueda.etiqet.core.message.cdr.Cdr;
import com.neueda.etiqet.core.transport.Codec;
import com.neueda.etiqet.core.transport.delegate.BinaryMessageConverterDelegate;
import com.neueda.etiqet.transport.jms.DestinationType;
import com.neueda.etiqet.transport.jms.dataformat.CodecDataFormat;
import com.neueda.etiqet.transport.jms.processor.ListenerProcessor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.DataFormat;

import java.util.function.Consumer;

import static com.neueda.etiqet.transport.jms.DestinationType.QUEUE;
import static com.neueda.etiqet.transport.jms.DestinationType.TOPIC;

public class JmsRouteBuilder extends RouteBuilder {
    private final DataFormat codecDataFormat;

    private static final String DESTINATION_TYPE = "jms.destinationType";
    public static final String DESTINATION_NAME = "jms.destinationName";

    public JmsRouteBuilder(final Codec codec, BinaryMessageConverterDelegate binaryMessageConverterDelegate) {
        super();
        codecDataFormat = new CodecDataFormat(codec, binaryMessageConverterDelegate);
    }

    @Override
    public void configure() throws Exception {

        from("direct:sendToTopic")
            .setHeader(DESTINATION_TYPE, constant(TOPIC.getType()))
            .to("direct:send");

        from("vm:sendToQueue")
            .setHeader(DESTINATION_TYPE, constant(QUEUE.getType()))
            .to("direct:send");

        from("direct:send")
            .marshal(codecDataFormat)
            .log("${header.jms.destinationType}")
            .toD("jms:${header.jms.destinationType}:${header.jms.destinationName}");
    }

    public RouteDefinition createListenerRouteDefinition(final DestinationType destinationType, final String destinationName, Consumer<Cdr> consumer) {
        return from("jms:" + destinationType.getType() + ":" + destinationName)
            .unmarshal(codecDataFormat)
            .process(new ListenerProcessor(consumer))
            .to("vm:allMessages");
    }



}
