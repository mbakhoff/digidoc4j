/* DigiDoc4J library
*
* This software is released under either the GNU Library General Public
* License (see LICENSE.LGPL).
*
* Note that the only valid version of the LGPL license as far as this
* project is concerned is the original GNU Library General Public License
* Version 2.1, February 1999
*/

package org.digidoc4j.impl.bdoc;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.digidoc4j.Configuration;
import org.digidoc4j.Container;
import org.digidoc4j.DataFile;
import org.digidoc4j.DigestAlgorithm;
import org.digidoc4j.Signature;
import org.digidoc4j.SignatureParameters;
import org.digidoc4j.SignatureProfile;
import org.digidoc4j.SignatureToken;
import org.digidoc4j.SignedInfo;
import org.digidoc4j.ValidationResult;
import org.digidoc4j.exceptions.DigiDoc4JException;
import org.digidoc4j.exceptions.DuplicateDataFileException;
import org.digidoc4j.exceptions.NotSupportedException;
import org.digidoc4j.exceptions.NotYetImplementedException;
import org.digidoc4j.exceptions.RemovingDataFileException;
import org.digidoc4j.exceptions.TechnicalException;
import org.digidoc4j.impl.bdoc.asic.AsicContainerCreator;
import org.digidoc4j.impl.bdoc.asic.DetachedContentCreator;
import org.digidoc4j.impl.bdoc.xades.SignatureExtender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.europa.esig.dss.DSSDocument;

public abstract class BDocContainer implements Container {

  private static final Logger logger = LoggerFactory.getLogger(BDocContainer.class);
  private Configuration configuration;
  private ValidationResult validationResult;

  public BDocContainer() {
    logger.debug("Instantiating BDoc container");
    configuration = new Configuration();
  }

  public BDocContainer(Configuration configuration) {
    logger.debug("Instantiating BDoc container with configuration");
    this.configuration = configuration;
  }

  protected abstract ValidationResult validateContainer();

  protected abstract void writeAsicContainer(AsicContainerCreator zipCreator);

  @Override
  public String getType() {
    return "BDOC";
  }

  @Override
  public ValidationResult validate() {
    if (validationResult == null) {
      validationResult = validateContainer();
    }
    return validationResult;
  }

  @Override
  public File saveAsFile(String filePath) {
    logger.debug("Saving container to file: " + filePath);
    File file = new File(filePath);
    AsicContainerCreator zipCreator = new AsicContainerCreator(file);
    writeAsicContainer(zipCreator);
    logger.info("Container was saved to file " + filePath);
    return file;
  }

  @Override
  public InputStream saveAsStream() {
    logger.debug("Saving container as stream");
    AsicContainerCreator zipCreator = new AsicContainerCreator();
    writeAsicContainer(zipCreator);
    InputStream inputStream = zipCreator.fetchInputStreamOfFinalizedContainer();
    logger.info("Container was saved to stream");
    return inputStream;
  }

  public Configuration getConfiguration() {
    return configuration;
  }

  protected List<Signature> parseSignatureFiles(List<DSSDocument> signatureFiles, List<DSSDocument> detachedContents) {
    Configuration configuration = getConfiguration();
    BDocSignatureOpener signatureOpener = new BDocSignatureOpener(detachedContents, configuration);
    List<Signature> signatures = new ArrayList<>(signatureFiles.size());
    for (DSSDocument signatureFile : signatureFiles) {
      List<BDocSignature> bDocSignatures = signatureOpener.parse(signatureFile);
      signatures.addAll(bDocSignatures);
    }
    return signatures;
  }

  protected void validateIncomingSignature(Signature signature) {
    if (!(signature instanceof BDocSignature)) {
      throw new TechnicalException("BDoc signature must be an instance of BDocSignature");
    }
  }

  protected List<Signature> extendAllSignaturesProfile(SignatureProfile profile, List<Signature> signatures, List<DataFile> dataFiles) {
    logger.info("Extending all signatures' profile to " + profile.name());
    validatePossibilityToExtendTo(profile);
    DetachedContentCreator detachedContentCreator = new DetachedContentCreator().populate(dataFiles);
    DSSDocument firstDetachedContent = detachedContentCreator.getFirstDetachedContent();
    List<DSSDocument> detachedContentList = detachedContentCreator.getDetachedContentList();
    SignatureExtender signatureExtender = new SignatureExtender(getConfiguration(), firstDetachedContent);
    Collection<DSSDocument> signatureDocuments = generateSignatureDocumentsList(signatures);
    List<DSSDocument> extendedSignatureDocuments = signatureExtender.extend(signatureDocuments, profile);
    List<Signature> extendedSignatures = parseSignatureFiles(extendedSignatureDocuments, detachedContentList);
    logger.debug("Finished extending all signatures");
    return extendedSignatures;
  }

  protected void validateDataFilesRemoval() {
    if (!getSignatures().isEmpty()) {
      logger.error("Datafiles cannot be removed from an already signed container");
      throw new RemovingDataFileException();
    }
  }

  protected void verifyIfAllowedToAddDataFile(String fileName) {
    if (getSignatures().size() > 0) {
      String errorMessage = "Datafiles cannot be added to an already signed container";
      logger.error(errorMessage);
      throw new DigiDoc4JException(errorMessage);
    }
    checkForDuplicateDataFile(fileName);
  }

  private void checkForDuplicateDataFile(String fileName) {
    logger.debug("");
    for (DataFile dataFile : getDataFiles()) {
      String dataFileName = dataFile.getName();
      if (StringUtils.equals(dataFileName, fileName)) {
        String errorMessage = "Data file " + fileName + " already exists";
        logger.error(errorMessage);
        throw new DuplicateDataFileException(errorMessage);
      }
    }
  }

  private void validatePossibilityToExtendTo(SignatureProfile profile) {
    logger.debug("Validating if it's possible to extend all the signatures to " + profile);
    for (Signature signature : getSignatures()) {
      if (profile == signature.getProfile()) {
        String errorMessage = "It is not possible to extend the signature to the same level";
        logger.error(errorMessage);
        throw new DigiDoc4JException(errorMessage);
      }
    }
  }

  private Collection<DSSDocument> generateSignatureDocumentsList(List<Signature> signatures) {
    List<DSSDocument> signatureDocuments = new ArrayList<>();
    for(Signature signature: signatures) {
      DSSDocument document = ((BDocSignature) signature).getSignatureDocument();
      signatureDocuments.add(document);
    }
    return signatureDocuments;
  }

  @Override
  @Deprecated
  public void save(OutputStream out) {
    try {
      InputStream inputStream = saveAsStream();
      IOUtils.copy(inputStream, out);
    } catch (IOException e) {
      logger.error("Error saving container input stream to output stream: " + e.getMessage());
      throw new TechnicalException("Error saving container input stream to output stream", e);
    }
  }

  @Override
  @Deprecated
  public void addRawSignature(byte[] signature) {
    logger.warn("Not yet implemented");
    throw new NotYetImplementedException();
  }

  @Override
  @Deprecated
  public void addRawSignature(InputStream signatureStream) {
    logger.warn("Not yet implemented");
    throw new NotYetImplementedException();
  }

  @Override
  @Deprecated
  public int countDataFiles() {
    return getDataFiles().size();
  }

  @Override
  @Deprecated
  public int countSignatures() {
    return getSignatures().size();
  }

  @Override
  @Deprecated
  public DocumentType getDocumentType() {
    return Container.DocumentType.BDOC;
  }

  @Override
  @Deprecated
  public String getVersion() {
    return null;
  }

  @Override
  @Deprecated
  public void extendTo(SignatureProfile profile) {
    extendSignatureProfile(profile);
  }

  @Override
  @Deprecated
  public void save(String path) {
    saveAsFile(path);
  }

  @Override
  @Deprecated
  public DataFile getDataFile(int index) {
    return getDataFiles().get(index);
  }

  @Override
  @Deprecated
  public Signature getSignature(int index) {
    return getSignatures().get(index);
  }

  @Override
  @Deprecated
  public SignedInfo prepareSigning(X509Certificate signerCert) {
    throw new NotSupportedException("Prepare signing method is not supported by BDoc container");
  }

  @Override
  @Deprecated
  public String getSignatureProfile() {
    throw new NotSupportedException("Getting signature profile method is not supported by BDoc container");
  }

  @Override
  @Deprecated
  public void setSignatureParameters(SignatureParameters signatureParameters) {
    throw new NotSupportedException("Setting signature parameters method is not supported by BDoc container");
  }

  @Override
  @Deprecated
  public DigestAlgorithm getDigestAlgorithm() {
    throw new NotSupportedException("Getting digest algorithm method is not supported by BDoc container");
  }

  @Override
  @Deprecated
  public Signature sign(SignatureToken signatureToken) {
    throw new NotSupportedException("Sign method is not supported by BDoc container");
  }

  @Override
  @Deprecated
  public Signature signRaw(byte[] rawSignature) {
    throw new NotSupportedException("Sign raw method is not supported by BDoc container");
  }

  @Override
  @Deprecated
  public void setSignatureProfile(SignatureProfile profile) {
    throw new NotSupportedException("Setting signature profile method is not supported by BDoc container");
  }
}
