package dev.rluo.authagain.auth;

import net.raphimc.minecraftauth.msa.model.MsaDeviceCode;

/**
 * <strong>DeviceCodePrompt</strong><br>
 * Represents a pending login. {@link ReauthService#startDeviceCodeLogin} gives this to the GUI to show the user the info to login.
 *
 * @param userCode              code the user types on the verification page
 * @param verificationUri       page for user to enter {@code userCode} on
 * @param directVerificationUri page that pre-fills {@code userCode}
 * @param expiresAtMs           last valid epoch time for the code
 */
public record DeviceCodePrompt(
        String userCode,
        String verificationUri,
        String directVerificationUri,
        long expiresAtMs) {

    static DeviceCodePrompt from(MsaDeviceCode code) {
        return new DeviceCodePrompt(
                code.getUserCode(),
                code.getVerificationUri(),
                code.getDirectVerificationUri(),
                code.getExpireTimeMs());
    }
}
