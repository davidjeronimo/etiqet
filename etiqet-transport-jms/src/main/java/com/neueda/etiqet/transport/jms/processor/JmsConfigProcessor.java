package com.neueda.etiqet.transport.jms.processor;

import com.neueda.etiqet.transport.jms.JmsConfiguration;
import com.neueda.etiqet.transport.jms.config.JmsConfigExtractor;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

public class JmsConfigProcessor implements Processor {



    @Override
    public void process(Exchange exchange) throws Exception {
        JmsConfiguration xmlConfiguration = (JmsConfiguration) exchange.getIn().getBody();
        JmsConfigExtractor configExtractor = new JmsConfigExtractor(null);
        exchange.getIn().setBody(configExtractor.transformConfiguration(xmlConfiguration));
    }
}
