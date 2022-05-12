/* DigiDoc4J library
 *
 * This software is released under either the GNU Library General Public
 * License (see LICENSE.LGPL).
 *
 * Note that the only valid version of the LGPL license as far as this
 * project is concerned is the original GNU Library General Public License
 * Version 2.1, February 1999
 */

package org.digidoc4j.exceptions;

/**
 * An exception signalling a TSL validation error.
 */
public class TslValidationException extends TslRefreshException {

  /**
   * Constructs a new TSL validation exception with the specified detail message.
   *
   * @param message the detail message
   */
  public TslValidationException(String message) {
    super(message);
  }

  /**
   * Constructs a new TSL validation exception with the specified detail message and cause.
   *
   * @param message the detail message
   * @param cause the cause of this exception
   */
  public TslValidationException(String message, Throwable cause) {
    super(message, cause);
  }

}
