/* DigiDoc4J library
 *
 * This software is released under either the GNU Library General Public
 * License (see LICENSE.LGPL).
 *
 * Note that the only valid version of the LGPL license as far as this
 * project is concerned is the original GNU Library General Public License
 * Version 2.1, February 1999
 */

package org.digidoc4j.impl.asic.tsl;

import eu.europa.esig.dss.model.DSSException;
import eu.europa.esig.dss.spi.client.http.DSSFileLoader;
import eu.europa.esig.dss.spi.x509.KeyStoreCertificateSource;
import eu.europa.esig.dss.tsl.alerts.TLAlert;
import eu.europa.esig.dss.tsl.alerts.detections.TLExpirationDetection;
import eu.europa.esig.dss.tsl.alerts.detections.TLSignatureErrorDetection;
import eu.europa.esig.dss.tsl.alerts.handlers.log.LogTLExpirationAlertHandler;
import eu.europa.esig.dss.tsl.alerts.handlers.log.LogTLSignatureErrorAlertHandler;
import eu.europa.esig.dss.tsl.function.EULOTLOtherTSLPointer;
import eu.europa.esig.dss.tsl.function.EUTLOtherTSLPointer;
import eu.europa.esig.dss.tsl.function.SchemeTerritoryOtherTSLPointer;
import eu.europa.esig.dss.tsl.function.XMLOtherTSLPointer;
import eu.europa.esig.dss.tsl.job.TLValidationJob;
import eu.europa.esig.dss.tsl.source.LOTLSource;
import eu.europa.esig.dss.tsl.sync.ExpirationAndSignatureCheckStrategy;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.digidoc4j.Configuration;
import org.digidoc4j.TSLRefreshCallback;
import org.digidoc4j.exceptions.DigiDoc4JException;
import org.digidoc4j.exceptions.LotlTrustStoreNotFoundException;
import org.digidoc4j.exceptions.TslCertificateSourceInitializationException;
import org.digidoc4j.utils.ResourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * TSL loader
 */
public class TslLoader implements Serializable {

  public static final File fileCacheDirectory = new File(System.getProperty("java.io.tmpdir") + "/digidoc4jTSLCache");
  private static final Logger logger = LoggerFactory.getLogger(TslLoader.class);
  private transient TSLCertificateSourceImpl tslCertificateSource;
  private transient TLValidationJob tlValidationJob;
  private final Configuration configuration;

  /**
   * @param configuration configuration context
   */
  public TslLoader(Configuration configuration) {
    this.configuration = configuration;
  }

  public static void invalidateCache() {
    logger.info("Cleaning TSL cache directory at {}", TslLoader.fileCacheDirectory.getPath());
    try {
      if (TslLoader.fileCacheDirectory.exists()) {
        FileUtils.cleanDirectory(TslLoader.fileCacheDirectory);
      } else {
        logger.debug("TSL cache directory doesn't exist");
      }
    } catch (Exception e) {
      throw new DigiDoc4JException(e);
    }
  }

  public void prepareTsl() {
    try {
      this.tslCertificateSource = new TSLCertificateSourceImpl();
      this.tlValidationJob = this.createTslValidationJob();
    } catch (DSSException e) {
      throw new TslCertificateSourceInitializationException("Failed to initialize TSL: " + e.getMessage(), e);
    }
  }

  public TSLRefreshCallback getTslRefreshCallback() {
    return Optional.ofNullable(configuration.getTslRefreshCallback())
            .orElseGet(() -> new DefaultTSLRefreshCallback(configuration));
  }

  private TLValidationJob createTslValidationJob() {
    TLValidationJob job = new TLValidationJob();

    DSSFileLoader tslFileLoader = new TslFileLoaderFactory(this.configuration, fileCacheDirectory).create();
    job.setOnlineDataLoader(tslFileLoader);

    LOTLSource lotlSource = createLOTLSource();
    job.setListOfTrustedListSources(lotlSource);
    job.setTrustedListCertificateSource(this.tslCertificateSource);
    job.setSynchronizationStrategy(new ExpirationAndSignatureCheckStrategy());

    job.setTLAlerts(Arrays.asList(tlSigningAlert(), tlExpirationDetection()));

    return job;
  }
  
  public TLAlert tlSigningAlert() {
    TLSignatureErrorDetection signingDetection = new TLSignatureErrorDetection();
    LogTLSignatureErrorAlertHandler handler = new LogTLSignatureErrorAlertHandler();
    return new TLAlert(signingDetection, handler);
  }

  public TLAlert tlExpirationDetection() {
    TLExpirationDetection expirationDetection = new TLExpirationDetection();
    LogTLExpirationAlertHandler handler = new LogTLExpirationAlertHandler();
    return new TLAlert(expirationDetection, handler);
  }

  private LOTLSource createLOTLSource() {
    LOTLSource lotlSource = new LOTLSource();
    lotlSource.setUrl(this.configuration.getLotlLocation());
    lotlSource.setCertificateSource(this.tslCertificateSource);
    lotlSource.setPivotSupport(this.configuration.isLotlPivotSupportEnabled());

    lotlSource.setCertificateSource(getTrustStore());
    Set<String> trustedTerritories = new HashSet<>();
    CollectionUtils.addAll(trustedTerritories, this.configuration.getTrustedTerritories());

    lotlSource.setLotlPredicate(new EULOTLOtherTSLPointer()
            .and(new XMLOtherTSLPointer())
    );

    if (!trustedTerritories.isEmpty()) {
      lotlSource.setTlPredicate(new SchemeTerritoryOtherTSLPointer(trustedTerritories).and(new EUTLOtherTSLPointer()
              .and(new XMLOtherTSLPointer()))
      );
    }

    return lotlSource;
  }


  private KeyStoreCertificateSource getTrustStore() {
    try (InputStream lotlTrustStoreInputStream = openLotlTrustStoreInputStream()) {
      return new KeyStoreCertificateSource(lotlTrustStoreInputStream,
              configuration.getLotlTruststoreType(),
              configuration.getLotlTruststorePassword());
    } catch (IOException e) {
      throw new LotlTrustStoreNotFoundException("Unable to retrieve trust-store", e);
    }
  }

  private InputStream openLotlTrustStoreInputStream() throws IOException, LotlTrustStoreNotFoundException {
    String trustStorePath = this.configuration.getLotlTruststorePath();
    try {
      return ResourceUtils.getResource(trustStorePath);
    } catch (Exception e) {
      throw new LotlTrustStoreNotFoundException(
              "Unable to retrieve LOTL trust-store from path: " + trustStorePath, e
      );
    }
  }

  /*
   * ACCESSORS
   */


  public TSLCertificateSourceImpl getTslCertificateSource() {
    return tslCertificateSource;
  }

  public TLValidationJob getTlValidationJob() {
    return tlValidationJob;
  }

}
