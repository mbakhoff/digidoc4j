package org.digidoc4j;

import eu.europa.esig.dss.model.x509.CertificateToken;
import org.digidoc4j.exceptions.CertificateValidationException;
import org.digidoc4j.impl.asic.tsl.TSLCertificateSourceImpl;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Paths;

/**
 * Created by Janar Rahumeel (CGI Estonia)
 */
public class CertificateValidatorBuilderTest extends AbstractTest {

  @Test
  public void findOnlyOneIssuerWhenCNAreSame() throws Exception {
    CertificateValidator validator = new CertificateValidatorBuilder().withConfiguration(this.configuration).build();
    validator.getCertificateSource().addCertificate(new CertificateToken(this.openX509Certificate(Paths.get
        ("src/test/resources/testFiles/certs/sameCN_first.crt"))));
    validator.getCertificateSource().addCertificate(new CertificateToken(this.openX509Certificate(Paths.get
        ("src/test/resources/testFiles/certs/sameCN_second.crt"))));
    try {
      validator.validate(this.openX509Certificate(Paths.get("src/test/resources/testFiles/certs/sameCN_first_child.crt")));
    } catch (CertificateValidationException e) {
      Assert.assertEquals("Not equals", CertificateValidationException.CertificateValidationStatus.UNKNOWN, e
          .getCertificateStatus());
    }
  }

  @Test
  public void testCertificateStatusGood() {
    CertificateValidator validator = new CertificateValidatorBuilder().withConfiguration(this.configuration).build();
    validator.getCertificateSource().addCertificate(new CertificateToken(this.openX509Certificate(Paths.get
        ("src/test/resources/testFiles/certs/TESTofEECertificationCentreRootCA.crt"))));
    validator.validate(
        this.openX509Certificate(Paths.get("src/test/resources/testFiles/certs/TESTofESTEID-SK2011.crt")));
  }

  @Test
  public void testCertificateStatusUntrusted() {
    CertificateValidator validator = new CertificateValidatorBuilder().withConfiguration(this.configuration).build();
    try {
      validator.validate(
          this.openX509Certificate(Paths.get("src/test/resources/testFiles/certs/TESTofESTEID-SK2011.crt")));
    } catch (CertificateValidationException e) {
      Assert.assertEquals("Not equals", CertificateValidationException.CertificateValidationStatus.UNTRUSTED, e
          .getCertificateStatus());
    }
  }

  @Test
  public void testCertificateStatusRevoked() {
    CertificateValidator validator = new CertificateValidatorBuilder().withConfiguration(this.configuration).build();
    validator.getCertificateSource().addCertificate(new CertificateToken(this.openX509Certificate(Paths.get
        ("src/test/resources/testFiles/certs/TESTofESTEID-SK2011.crt"))));
    try {
      validator.validate(
          this.openX509Certificate(Paths.get("src/test/resources/testFiles/certs/TESTofStatusRevoked.cer")));
    } catch (CertificateValidationException e) {
      Assert.assertEquals("Not equals", CertificateValidationException.CertificateValidationStatus.REVOKED, e
          .getCertificateStatus());
    }
  }

  @Test
  public void testProductionCertificateStatusUntrustedWithMissingOCSPResponseCertificate() {
    this.configuration = Configuration.of(Configuration.Mode.PROD);
    this.configuration.setTSL(new TSLCertificateSourceImpl());
    CertificateValidator validator = new CertificateValidatorBuilder().withConfiguration(this.configuration).build();
    try {
      validator.getCertificateSource().addCertificate(new CertificateToken(this.openX509Certificate(Paths.get
          ("src/test/resources/testFiles/certs/TESTofESTEID-SK2011.crt"))));
      validator.validate(
          this.openX509Certificate(Paths.get("src/test/resources/testFiles/certs/TESTofStatusRevoked.cer")));
    } catch (CertificateValidationException e) {
      Assert.assertEquals("Not equals", CertificateValidationException.CertificateValidationStatus.UNTRUSTED, e
          .getCertificateStatus());
    }
  }

  @Test
  public void testProductionCertificateStatusUnknownWithOCSPResponseVerificationCertificate() {
    this.configuration = Configuration.of(Configuration.Mode.PROD);
    CertificateValidator validator = new CertificateValidatorBuilder().withConfiguration(this.configuration).build();
    try {
      validator.getCertificateSource().addCertificate(new CertificateToken(this.openX509Certificate(Paths.get
          ("src/test/resources/testFiles/certs/TESTofESTEID-SK2011.crt"))));
      validator.validate(
          this.openX509Certificate(Paths.get("src/test/resources/testFiles/certs/TESTofStatusRevoked.cer")));
    } catch (CertificateValidationException e) {
      Assert.assertEquals("Not equals", CertificateValidationException.CertificateValidationStatus.UNKNOWN, e
          .getCertificateStatus());
    }
  }

  @Test
  public void testLoadingOCSPIntermediateCertificatesFromCustomLocation() {
    ExtendedCertificateSource source = CertificateValidatorBuilder.getDefaultCertificateSource();
    source.importFromPath(Paths.get("src/test/resources/testFiles/certs"));
    Assert.assertEquals("Not equals", 13, source.getCertificates().size());
  }

  /*
   * RESTRICTED METHODS
   */

  @Override
  protected void before() {
    this.configuration = new Configuration(Configuration.Mode.TEST);
  }

}
