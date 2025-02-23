package org.digidoc4j.impl.bdoc.asic;

import eu.europa.esig.dss.service.http.commons.CommonsDataLoader;
import eu.europa.esig.dss.spi.client.http.DataLoader;
import org.digidoc4j.AbstractTest;
import org.digidoc4j.Configuration;
import org.digidoc4j.Container;
import org.digidoc4j.DataLoaderFactory;
import org.digidoc4j.DataToSign;
import org.digidoc4j.Signature;
import org.digidoc4j.SignatureBuilder;
import org.digidoc4j.SignatureFinalizerBuilder;
import org.digidoc4j.SignatureProfile;
import org.digidoc4j.impl.SignatureFinalizer;
import org.digidoc4j.impl.SkOCSPDataLoader;
import org.digidoc4j.impl.SkTimestampDataLoader;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class AsicSignatureFinalizerTest extends AbstractTest {

  @Test
  public void bdocSignatureFinalization() {
    Container container = createEmptyContainerBy(Container.DocumentType.BDOC);
    container.addDataFile(new ByteArrayInputStream("something".getBytes(StandardCharsets.UTF_8)), "file name", "text/plain");

    DataToSign dataToSign = SignatureBuilder.aSignature(container)
          .withSigningCertificate(pkcs12SignatureToken.getCertificate())
          .withSignatureProfile(SignatureProfile.LT_TM)
          .buildDataToSign();

    byte[] signatureDigest = sign(dataToSign.getDataToSign(), dataToSign.getDigestAlgorithm());

    SignatureFinalizer signatureFinalizer = SignatureFinalizerBuilder.aFinalizer(container, dataToSign.getSignatureParameters());
    Signature signature = signatureFinalizer.finalizeSignature(signatureDigest);
    assertTimemarkSignature(signature);
    assertValidSignature(signature);
  }

  @Test
  public void asicESignatureFinalization() {
    Container container = createEmptyContainerBy(Container.DocumentType.ASICE);
    container.addDataFile(new ByteArrayInputStream("something".getBytes(StandardCharsets.UTF_8)), "file name", "text/plain");

    DataToSign dataToSign = SignatureBuilder.aSignature(container)
          .withSigningCertificate(pkcs12SignatureToken.getCertificate())
          .withSignatureProfile(SignatureProfile.LT)
          .buildDataToSign();

    byte[] signatureDigest = sign(dataToSign.getDataToSign(), dataToSign.getDigestAlgorithm());

    SignatureFinalizer signatureFinalizer = SignatureFinalizerBuilder.aFinalizer(container, dataToSign.getSignatureParameters());
    Signature signature = signatureFinalizer.finalizeSignature(signatureDigest);
    assertTimestampSignature(signature);
    assertValidSignature(signature);
  }

  @Test
  public void signatureFinalizerFieldsEqualToDataToSign() {
    Container container = createEmptyContainerBy(Container.DocumentType.ASICE);
    container.addDataFile(new ByteArrayInputStream("something".getBytes(StandardCharsets.UTF_8)), "file name", "text/plain");

    DataToSign dataToSign = SignatureBuilder.aSignature(container)
          .withSigningCertificate(pkcs12SignatureToken.getCertificate())
          .withSignatureProfile(SignatureProfile.LT)
          .buildDataToSign();

    SignatureFinalizer signatureFinalizer = SignatureFinalizerBuilder.aFinalizer(container, dataToSign.getSignatureParameters());
    assertEquals(dataToSign.getSignatureParameters(), signatureFinalizer.getSignatureParameters());
    assertEquals(dataToSign.getConfiguration(), signatureFinalizer.getConfiguration());
    assertEquals(dataToSign.getDigestAlgorithm(), signatureFinalizer.getSignatureParameters().getSignatureDigestAlgorithm());
  }

  @Test
  public void getDataToSignBytesEqualToValueFromDataToSignObject() {
    Container container = createEmptyContainerBy(Container.DocumentType.ASICE);
    container.addDataFile(new ByteArrayInputStream("something".getBytes(StandardCharsets.UTF_8)), "file name", "text/plain");

    DataToSign dataToSign = SignatureBuilder.aSignature(container)
          .withSigningCertificate(pkcs12SignatureToken.getCertificate())
          .withSignatureProfile(SignatureProfile.LT)
          .buildDataToSign();

    byte[] dataToSignBytes = dataToSign.getDataToSign();
    byte[] signatureDigest = sign(dataToSignBytes, dataToSign.getDigestAlgorithm());

    SignatureFinalizer signatureFinalizer = SignatureFinalizerBuilder.aFinalizer(container, dataToSign.getSignatureParameters());

    assertThat(dataToSignBytes, equalTo(signatureFinalizer.getDataToBeSigned()));

    Signature signature = signatureFinalizer.finalizeSignature(signatureDigest);
    assertTimestampSignature(signature);
    assertValidSignature(signature);

    assertThat(dataToSignBytes, equalTo(signatureFinalizer.getDataToBeSigned()));
  }

  @Test
  public void testCustomTspDataLoaderUsedForSigning() {
    configuration = Configuration.of(Configuration.Mode.TEST);
    SkTimestampDataLoader tspDataLoader = new SkTimestampDataLoader(configuration);
    tspDataLoader.setUserAgent("custom-user-agent-string");
    DataLoader dataLoaderSpy = Mockito.spy(tspDataLoader);

    DataLoaderFactory dataLoaderFactory = Mockito.mock(DataLoaderFactory.class);
    Mockito.doReturn(dataLoaderSpy).when(dataLoaderFactory).create();
    configuration.setTspDataLoaderFactory(dataLoaderFactory);

    Signature signature = createSignatureBy(createNonEmptyContainerByConfiguration(), pkcs12SignatureToken);
    assertValidSignature(signature);

    Mockito.verify(dataLoaderFactory, Mockito.times(1)).create();
    Mockito.verify(dataLoaderSpy, Mockito.times(1)).post(Mockito.eq(configuration.getTspSource()), Mockito.any(byte[].class));
    Mockito.verifyNoMoreInteractions(dataLoaderFactory);
  }

  @Test
  public void testCustomOcspDataLoaderUsedForSigning() {
    configuration = Configuration.of(Configuration.Mode.TEST);
    SkOCSPDataLoader ocspDataLoader = new SkOCSPDataLoader(configuration);
    ocspDataLoader.setUserAgent("custom-user-agent-string");
    DataLoader dataLoaderSpy = Mockito.spy(ocspDataLoader);

    DataLoaderFactory dataLoaderFactory = Mockito.mock(DataLoaderFactory.class);
    Mockito.doReturn(dataLoaderSpy).when(dataLoaderFactory).create();
    configuration.setOcspDataLoaderFactory(dataLoaderFactory);

    Signature signature = createSignatureBy(createNonEmptyContainerByConfiguration(), pkcs12SignatureToken);
    assertValidSignature(signature);

    Mockito.verify(dataLoaderFactory, Mockito.times(1)).create();
    Mockito.verify(dataLoaderSpy, Mockito.times(1)).post(Mockito.eq(configuration.getOcspSource()), Mockito.any(byte[].class));
    Mockito.verifyNoMoreInteractions(dataLoaderFactory);
  }

  @Test
  public void testCustomAiaDataLoaderUsedForSigning() {
    configuration = Configuration.of(Configuration.Mode.TEST);
    CommonsDataLoader aiaDataLoader = new CommonsDataLoader();
    DataLoader dataLoaderSpy = Mockito.spy(aiaDataLoader);

    DataLoaderFactory dataLoaderFactory = Mockito.mock(DataLoaderFactory.class);
    Mockito.doReturn(dataLoaderSpy).when(dataLoaderFactory).create();
    configuration.setAiaDataLoaderFactory(dataLoaderFactory);

    Signature signature = createSignatureBy(createNonEmptyContainerByConfiguration(), pkcs12SignatureToken);
    assertValidSignature(signature);

    Mockito.verify(dataLoaderFactory, Mockito.atLeast(1)).create();
    Mockito.verify(dataLoaderSpy, Mockito.times(1)).get("https://www.sk.ee/upload/files/TEST_of_EE_Certification_Centre_Root_CA.der.crt");
    Mockito.verifyNoMoreInteractions(dataLoaderFactory);
  }
}
