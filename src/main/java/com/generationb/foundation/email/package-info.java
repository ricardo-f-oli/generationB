/**
 * Outbound email. Exposed as a named interface so other modules can depend on
 * {@code EmailSender} without reaching into foundation's internals.
 */
@org.springframework.modulith.NamedInterface("email")
package com.generationb.foundation.email;
