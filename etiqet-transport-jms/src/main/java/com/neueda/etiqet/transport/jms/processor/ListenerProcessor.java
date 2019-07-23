package com.neueda.etiqet.transport.jms.processor;


import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import java.util.function.Consumer;

public class ListenerProcessor implements Processor {
    private Consumer consumer;

    public ListenerProcessor(Consumer consumer) {
        this.consumer = consumer;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Object body = exchange.getIn().getBody();
        consumer.accept(body);
    }
}
