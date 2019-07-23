package com.neueda.etiqet.transport.jms.routes;

import com.neueda.etiqet.transport.jms.JmsConfiguration;
import com.neueda.etiqet.transport.jms.processor.JmsConfigProcessor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JaxbDataFormat;

public class JmsConfigRouteBuilder extends RouteBuilder {
    final String configFile;
    final JaxbDataFormat jaxbDataFormat;
    final String resourcesFolder = "/C:/Users/Neueda/projects/etiqet/etiqet-transport-jms/src/test/resources";

    public JmsConfigRouteBuilder(final String configFile) {
        super();
        this.configFile = configFile;
        jaxbDataFormat = new JaxbDataFormat();
        jaxbDataFormat.setContextPath(JmsConfiguration.class.getPackage().getName());
        //URL resourceURL = getClass().getResource("/");
        jaxbDataFormat.setSchema("classpath:config/etiqet_jms.xsd");
    }

    @Override
    public void configure() throws Exception {
        from("direct:loadConfig")
            //.pollEnrich().simple("file:/C:/Users/Neueda/projects/etiqet/etiqet-transport-jms/src/test/resources/config?fileName=jmsConfig.xml&noop=true").timeout(2000)
            .pollEnrich().simple("file:/C:/Users/Neueda/projects/etiqet/etiqet-transport-jms/src/test/resources/config?fileName=${body}&noop=true").timeout(2000)
            .unmarshal(jaxbDataFormat)
            .process(new JmsConfigProcessor())
            .setHeader("header", constant("header"))
            .to("direct:jmsConfiguration");
    }
}
