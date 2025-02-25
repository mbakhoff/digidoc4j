/* DigiDoc4J library
 *
 * This software is released under either the GNU Library General Public
 * License (see LICENSE.LGPL).
 *
 * Note that the only valid version of the LGPL license as far as this
 * project is concerned is the original GNU Library General Public License
 * Version 2.1, February 1999
 */

package org.digidoc4j.impl.asic;

import eu.europa.esig.dss.service.http.commons.CommonsDataLoader;
import eu.europa.esig.dss.service.http.proxy.ProxyConfig;
import eu.europa.esig.dss.service.http.proxy.ProxyProperties;
import org.digidoc4j.Configuration;
import org.digidoc4j.ExternalConnectionType;
import org.digidoc4j.utils.KeyStoreDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Period;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Data loader decorator
 */
public class DataLoaderDecorator {

  private final static Logger logger = LoggerFactory.getLogger(DataLoaderDecorator.class);
  /**
   * Minimum validation interval of 5 minutes should be enough for the most common case of TSL data loaders, targeting
   * key-store validation per each TSL cache update.
   * Every time the TSL cache is updated (e.g. once a day), TLS trust-stores and/or key-stores are accessed (if configured)
   * per TL fetch from each country. Minimum key-store validation interval should be long enough that the validation of the
   * key-store is not performed multiple times per update cycle, but short enough the validation would be invoked per
   * desired number of TSL updates.
   */
  private final static Duration MIN_KEYSTORE_VALIDATION_INTERVAL = Duration.ofMinutes(5L);
  /**
   * Maximum warning period determines how long before certificate expiration date should warning messages about the upcoming
   * expiration be logged.
   */
  private final static Period MAX_KEYSTORE_WARNING_PERIOD = Period.ofDays(60);

  /**
   * @param dataLoader    data loader
   * @param configuration configuration
   */
  public static void decorateWithProxySettings(CommonsDataLoader dataLoader, Configuration configuration) {
    if (configuration.isNetworkProxyEnabled()) {
      ProxyProperties httpProxyProperties = createProxyPropertiesIfHostAndPortPresent(
              configuration.getHttpProxyPort(), configuration.getHttpProxyHost(),
              configuration.getHttpProxyUser(), configuration.getHttpProxyPassword()
      );
      ProxyProperties httpsProxyProperties = createProxyPropertiesIfHostAndPortPresent(
              configuration.getHttpsProxyPort(), configuration.getHttpsProxyHost(),
              configuration.getHttpsProxyUser(), configuration.getHttpsProxyPassword()
      );
      ProxyConfig proxyConfig = createProxyConfigIfAnyPropertiesPresent(httpProxyProperties, httpsProxyProperties);
      dataLoader.setProxyConfig(proxyConfig);
    }
  }

  /**
   * @param connectionType type of external connections
   * @param dataLoader     data loader
   * @param configuration  configuration
   */
  public static void decorateWithProxySettingsFor(ExternalConnectionType connectionType, CommonsDataLoader dataLoader, Configuration configuration) {
    if (configuration.isNetworkProxyEnabledFor(connectionType)) {
      ProxyProperties httpProxyProperties = createProxyPropertiesIfHostAndPortPresent(
              configuration.getHttpProxyPortFor(connectionType), configuration.getHttpProxyHostFor(connectionType),
              configuration.getHttpProxyUserFor(connectionType), configuration.getHttpProxyPasswordFor(connectionType)
      );
      ProxyProperties httpsProxyProperties = createProxyPropertiesIfHostAndPortPresent(
              configuration.getHttpsProxyPortFor(connectionType), configuration.getHttpsProxyHostFor(connectionType),
              configuration.getHttpsProxyUserFor(connectionType), configuration.getHttpsProxyPasswordFor(connectionType)
      );
      ProxyConfig proxyConfig = createProxyConfigIfAnyPropertiesPresent(httpProxyProperties, httpsProxyProperties);
      dataLoader.setProxyConfig(proxyConfig);
    }
  }

  private static ProxyConfig createProxyConfigIfAnyPropertiesPresent(ProxyProperties httpProxyProperties, ProxyProperties httpsProxyProperties) {
    if (httpProxyProperties != null || httpsProxyProperties != null) {
      logger.debug("Creating proxy settings");
      ProxyConfig proxyConfig = new ProxyConfig();
      proxyConfig.setHttpProperties(httpProxyProperties);
      proxyConfig.setHttpsProperties(httpsProxyProperties);
      return proxyConfig;
    } else {
      return null;
    }
  }

  private static ProxyProperties createProxyPropertiesIfHostAndPortPresent(Integer proxyPort, String proxyHost, String proxyUser, String proxyPassword) {
    if (proxyPort != null && isNotBlank(proxyHost)) {
      ProxyProperties proxyProperties = new ProxyProperties();
      proxyProperties.setPort(proxyPort);
      proxyProperties.setHost(proxyHost);
      if (isNotBlank(proxyUser) && isNotBlank(proxyPassword)) {
        proxyProperties.setUser(proxyUser);
        proxyProperties.setPassword(proxyPassword);
      }
      return proxyProperties;
    } else {
      return null;
    }
  }

  /**
   * @param dataLoader    data loader
   * @param configuration configuration
   */
  public static void decorateWithSslSettings(CommonsDataLoader dataLoader, Configuration configuration) {
    if (configuration.isSslConfigurationEnabled()) {
      logger.debug("Configuring SSL");
      configureSslKeystore(dataLoader, configuration.getSslKeystorePath(),
              configuration.getSslKeystoreType(), configuration.getSslKeystorePassword());
      configureSslTruststore(dataLoader, configuration.getSslTruststorePath(),
              configuration.getSslTruststoreType(), configuration.getSslTruststorePassword());
      configureSslProtocol(dataLoader, configuration.getSslProtocol());
      configureSupportedSslProtocols(dataLoader, configuration.getSupportedSslProtocols());
      configureSupportedSslCipherSuites(dataLoader, configuration.getSupportedSslCipherSuites());
    }
  }

  /**
   * @param connectionType type of external connections
   * @param dataLoader     data loader
   * @param configuration  configuration
   */
  public static void decorateWithSslSettingsFor(ExternalConnectionType connectionType, CommonsDataLoader dataLoader, Configuration configuration) {
    if (configuration.isSslConfigurationEnabledFor(connectionType)) {
      logger.debug("Configuring SSL");
      configureSslKeystore(dataLoader, configuration.getSslKeystorePathFor(connectionType),
              configuration.getSslKeystoreTypeFor(connectionType), configuration.getSslKeystorePasswordFor(connectionType));
      configureSslTruststore(dataLoader, configuration.getSslTruststorePathFor(connectionType),
              configuration.getSslTruststoreTypeFor(connectionType), configuration.getSslTruststorePasswordFor(connectionType));
      configureSslProtocol(dataLoader, configuration.getSslProtocolFor(connectionType));
      configureSupportedSslProtocols(dataLoader, configuration.getSupportedSslProtocolsFor(connectionType));
      configureSupportedSslCipherSuites(dataLoader, configuration.getSupportedSslCipherSuitesFor(connectionType));
    }
  }

  private static void configureSslKeystore(CommonsDataLoader dataLoader, String sslKeystorePath, String sslKeystoreType, String sslKeystorePassword) {
    if (sslKeystorePath != null) {
      dataLoader.setSslKeystore(new KeyStoreDocument(sslKeystorePath, sslKeystoreType, sslKeystorePassword, MIN_KEYSTORE_VALIDATION_INTERVAL, MAX_KEYSTORE_WARNING_PERIOD));
      if (sslKeystoreType != null) {
        dataLoader.setSslKeystoreType(sslKeystoreType);
      }
      if (sslKeystorePassword != null) {
        dataLoader.setSslKeystorePassword(sslKeystorePassword);
      }
    }
  }

  private static void configureSslTruststore(CommonsDataLoader dataLoader, String sslTruststorePath, String sslTruststoreType, String sslTruststorePassword) {
    if (sslTruststorePath != null) {
      dataLoader.setSslTruststore(new KeyStoreDocument(sslTruststorePath, sslTruststoreType, sslTruststorePassword, MIN_KEYSTORE_VALIDATION_INTERVAL, MAX_KEYSTORE_WARNING_PERIOD));
      if (sslTruststoreType != null) {
        dataLoader.setSslTruststoreType(sslTruststoreType);
      }
      if (sslTruststorePassword != null) {
        dataLoader.setSslTruststorePassword(sslTruststorePassword);
      }
    }
  }

  private static void configureSslProtocol(CommonsDataLoader dataLoader, String sslProtocol) {
    if (sslProtocol != null) {
      dataLoader.setSslProtocol(sslProtocol);
    }
  }

  private static void configureSupportedSslProtocols(CommonsDataLoader dataLoader, List<String> supportedSslProtocols) {
    if (supportedSslProtocols != null) {
      dataLoader.setSupportedSSLProtocols(supportedSslProtocols.toArray(new String[supportedSslProtocols.size()]));
    }
  }

  private static void configureSupportedSslCipherSuites(CommonsDataLoader dataLoader, List<String> supportedSslCipherSuites) {
    if (supportedSslCipherSuites != null) {
      dataLoader.setSupportedSSLCipherSuites(supportedSslCipherSuites.toArray(new String[supportedSslCipherSuites.size()]));
    }
  }
}
