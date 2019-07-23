package com.neueda.etiqet.transport.jms.dataformat;

import com.neueda.etiqet.core.message.cdr.Cdr;
import com.neueda.etiqet.core.transport.Codec;
import com.neueda.etiqet.core.transport.delegate.BinaryMessageConverterDelegate;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.spi.DataFormat;

import java.io.InputStream;
import java.io.OutputStream;

public class CodecDataFormat<T> implements DataFormat {
    private final Codec<Cdr, T> codec;
    private final BinaryMessageConverterDelegate binaryMessageConverterDelegate;

    public CodecDataFormat(final Codec<Cdr, T> codec, final BinaryMessageConverterDelegate binaryMessageConverterDelegate) {
        this.codec = codec;
        this.binaryMessageConverterDelegate = binaryMessageConverterDelegate;
    }

    @Override
    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        Message message = exchange.getIn();
        T encodedMessage = codec.encode((Cdr) message.getBody());
        message.setBody(encodedMessage);
        final byte[] bytes;
        if (encodedMessage instanceof byte[]) {
            bytes = (byte[]) encodedMessage;
        } else if (encodedMessage instanceof String) {
            bytes = ((String) encodedMessage).getBytes();
        } else {
            bytes = binaryMessageConverterDelegate.toByteArray(encodedMessage);
        }
        stream.write(bytes);
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        final Object binaryMessage = exchange.getIn().getBody();
        final Object message;
        if (binaryMessage instanceof byte[] && binaryMessageConverterDelegate != null) {
            message = binaryMessageConverterDelegate.fromByteArray((byte[]) binaryMessage);
        } else {
            message = binaryMessage;
        }
        return codec.decode((T) message);
    }
}
