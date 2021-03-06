package ameba.container.grizzly.server.http;

import ameba.container.Container;
import ameba.container.grizzly.server.http.websocket.WebSocketAddOn;
import ameba.container.server.Connector;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import org.glassfish.grizzly.http.CompressionConfig;
import org.glassfish.grizzly.http.ajp.AjpAddOn;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http2.Http2AddOn;
import org.glassfish.grizzly.spdy.SpdyAddOn;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author icode
 */
public class GrizzlyServerUtil {
    public static final Logger logger = LoggerFactory.getLogger(GrizzlyServerUtil.class);
    public static final String DEFAULT_NETWORK_LISTENER_NAME = "ameba";
    public static final String COMPRESSION_MODE_KEY = "compression.mode";
    public static final String COMPRESSION_MIN_SIZE_KEY = "compression.minSize";
    public static final String COMPRESSION_MIME_TYPES_KEY = "compression.mimeTypes";
    public static final String COMPRESSION_USER_AGENTS_KEY = "compression.userAgents";


    @SuppressWarnings("unchecked")
    public static List<NetworkListener> createListeners(List<Connector> connectors, CompressionConfig compression) {
        List<NetworkListener> listeners = Lists.newArrayList();

        for (Connector connector : connectors) {
            final String host = (connector.getHost() == null) ? Connector.DEFAULT_NETWORK_HOST
                    : connector.getHost();
            final int port = (connector.getPort() == -1) ? 80 : connector.getPort();
            final NetworkListener listener = new NetworkListener(
                    StringUtils.defaultString(connector.getName(), DEFAULT_NETWORK_LISTENER_NAME),
                    host,
                    port);
            listener.setSecure(connector.isSecureEnabled());
            SSLEngineConfigurator sslEngineConfigurator = createSslEngineConfigurator(connector);
            if (sslEngineConfigurator != null) {
                listener.setSSLEngineConfig(sslEngineConfigurator);

                if (connector.isSecureEnabled() && !connector.isAjpEnabled()) {
                    listener.registerAddOn(new Http2AddOn());
                    listener.registerAddOn(new SpdyAddOn());
                }
            }

            if (connector.isAjpEnabled()) {
                listener.registerAddOn(new AjpAddOn());
            }

            CompressionConfig compressionConfig = listener.getCompressionConfig();
            CompressionConfig compressionCfg = createCompressionConfig(null, (Map) connector.getRawProperties());

            if (compression != null) {

                if (compressionCfg.getCompressionMode() == null) {
                    compressionCfg.setCompressionMode(compression.getCompressionMode());
                }

                if (compressionCfg.getCompressionMode() != CompressionConfig.CompressionMode.OFF) {
                    if (compressionCfg.getCompressionMinSize() < 512) {
                        compressionCfg.setCompressionMinSize(compression.getCompressionMinSize());
                    }

                    Set<String> mimeTypes = compressionCfg.getCompressibleMimeTypes();
                    Set<String> newTypes = Sets.newConcurrentHashSet(compression.getCompressibleMimeTypes());
                    newTypes.addAll(mimeTypes);
                    compressionCfg.setCompressibleMimeTypes(newTypes);

                    Set<String> agents = compressionCfg.getNoCompressionUserAgents();
                    Set<String> newAgents = Sets.newConcurrentHashSet(compression.getNoCompressionUserAgents());
                    newAgents.addAll(agents);
                    compressionCfg.setNoCompressionUserAgents(newAgents);
                }
            }

            if (compressionCfg != null) {
                compressionConfig.set(compressionCfg);
            }
            listener.setMaxHttpHeaderSize(98304);
            listeners.add(listener);
        }

        return listeners;
    }

    public static CompressionConfig createCompressionConfig(String keyPrefix, Map<String, Object> properties) {
        CompressionConfig compressionConfig;
        if (keyPrefix == null)
            keyPrefix = "";
        else if (!keyPrefix.endsWith("."))
            keyPrefix += ".";
        String modeStr = (String) properties.get(keyPrefix + COMPRESSION_MODE_KEY);

        if (!"ON".equalsIgnoreCase(modeStr) && !"FORCE".equalsIgnoreCase(modeStr)) {
            modeStr = CompressionConfig.CompressionMode.OFF.name();
        }

        String minSizeKey = keyPrefix + COMPRESSION_MIN_SIZE_KEY;
        String minSizeStr = (String) properties.get(minSizeKey);
        String mimeTypesStr = (String) properties.get(keyPrefix + COMPRESSION_MIME_TYPES_KEY);
        String userAgentsStr = (String) properties.get(keyPrefix + COMPRESSION_USER_AGENTS_KEY);

        compressionConfig = new CompressionConfig();
        compressionConfig.setCompressionMode(CompressionConfig.CompressionMode.fromString(modeStr)); // the mode
        if (StringUtils.isNotBlank(minSizeStr)) {
            try {
                int minSize = Integer.parseInt(minSizeStr); // the min amount of bytes to compress
                compressionConfig.setCompressionMinSize(minSize);
            } catch (Exception e) {
                logger.error("parse " + minSizeKey + " error", e);
            }
        }
        if (StringUtils.isNotBlank(mimeTypesStr))
            compressionConfig.setCompressibleMimeTypes(mimeTypesStr.split(",")); // the mime types to compress

        if (StringUtils.isNotBlank(userAgentsStr))
            compressionConfig.setNoCompressionUserAgents(userAgentsStr.split(","));
        return compressionConfig;
    }

    public static SSLEngineConfigurator createSslEngineConfigurator(Connector connector) {
        SSLEngineConfigurator sslEngineConfigurator = null;
        if (connector.isSslConfigReady()) {
            SSLContextConfigurator sslContextConfiguration = new SSLContextConfigurator();
            if (StringUtils.isNotBlank(connector.getSslKeyPassword()))
                sslContextConfiguration.setKeyPass(connector.getSslKeyPassword());
            if (StringUtils.isNotBlank(connector.getSslProtocol()))
                sslContextConfiguration.setSecurityProtocol(connector.getSslProtocol());

            sslContextConfiguration.setKeyStoreBytes(connector.getSslKeyStoreFile());
            if (StringUtils.isNotBlank(connector.getSslKeyStorePassword()))
                sslContextConfiguration.setKeyStorePass(connector.getSslKeyStorePassword());
            if (StringUtils.isNotBlank(connector.getSslKeyStoreProvider()))
                sslContextConfiguration.setKeyStoreProvider(connector.getSslKeyStoreProvider());
            if (StringUtils.isNotBlank(connector.getSslKeyStoreType()))
                sslContextConfiguration.setKeyStoreType(connector.getSslKeyStoreType());
            if (StringUtils.isNotBlank(connector.getSslKeyManagerFactoryAlgorithm()))
                sslContextConfiguration.setKeyManagerFactoryAlgorithm(connector.getSslKeyManagerFactoryAlgorithm());

            sslContextConfiguration.setTrustStoreBytes(connector.getSslTrustStoreFile());
            if (StringUtils.isNotBlank(connector.getSslTrustStorePassword()))
                sslContextConfiguration.setTrustStorePass(connector.getSslTrustStorePassword());
            if (StringUtils.isNotBlank(connector.getSslTrustStoreType()))
                sslContextConfiguration.setTrustStoreType(connector.getSslTrustStoreType());
            if (StringUtils.isNotBlank(connector.getSslTrustStoreProvider()))
                sslContextConfiguration.setTrustStoreProvider(connector.getSslTrustStoreProvider());
            if (StringUtils.isNotBlank(connector.getSslTrustManagerFactoryAlgorithm()))
                sslContextConfiguration.setTrustManagerFactoryAlgorithm(connector.getSslTrustManagerFactoryAlgorithm());

            sslEngineConfigurator = new SSLEngineConfigurator(
                    sslContextConfiguration,
                    connector.isSslClientMode(),
                    connector.isSslNeedClientAuth(),
                    connector.isSslWantClientAuth());
        }
        return sslEngineConfigurator;
    }

    public static void bindWebSocket(String contextPath, Container container, final List<NetworkListener> listeners) {
        org.glassfish.grizzly.http.server.AddOn addOn = new WebSocketAddOn(container, contextPath);

        for (NetworkListener listener : listeners) {
            // idle timeout set to indefinite.
            listener.getKeepAlive().setIdleTimeoutInSeconds(-1);
            listener.registerAddOn(addOn);
        }

    }
}
