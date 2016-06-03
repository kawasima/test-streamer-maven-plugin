package net.unit8.teststreamer.mojo;

import java.io.IOException;

/**
 * @author kawasima
 */
public class IORuntimeException extends RuntimeException {
    public IORuntimeException(IOException ex) {
        super(ex);
    }
}
