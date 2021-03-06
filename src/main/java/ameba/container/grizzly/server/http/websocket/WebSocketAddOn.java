package ameba.container.grizzly.server.http.websocket;

import ameba.container.Container;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.http.server.AddOn;
import org.glassfish.grizzly.http.server.HttpServerFilter;
import org.glassfish.grizzly.http.server.NetworkListener;

/**
 * @author icode
 */
public class WebSocketAddOn implements AddOn {

    private final Container container;
    private final String contextPath;

    public WebSocketAddOn(Container container, String contextPath) {
        this.container = container;
        this.contextPath = contextPath;
    }

    @Override
    public void setup(NetworkListener networkListener, FilterChainBuilder builder) {
        // Get the index of HttpServerFilter in the HttpServer filter chain
        final int httpServerFilterIdx = builder.indexOfType(HttpServerFilter.class);

        if (httpServerFilterIdx >= 0) {
            // Insert the WebSocketFilter right before HttpServerFilter
            builder.add(httpServerFilterIdx, new GrizzlyServerFilter(container, contextPath));
        }
    }
}