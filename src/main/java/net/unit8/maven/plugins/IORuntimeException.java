package net.unit8.maven.plugins;

import java.io.IOException;

/**
 * @author kawasima
 */
public class IORuntimeException extends RuntimeException {
    public IORuntimeException(IOException ex) {
        super(ex);
    }
}
