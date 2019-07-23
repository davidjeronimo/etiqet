package com.neueda.etiqet.transport.jms;

public enum DestinationType {
    TOPIC("topic"),
    QUEUE("queue");

    private String type;

    DestinationType(final String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
