package dev.rluo.authagain.auth;

import com.google.gson.JsonObject;
import dev.rluo.authagain.AuthAgainMod;
import net.lenni0451.commons.httpclient.HttpClient;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.java.JavaAuthManager;
import net.raphimc.minecraftauth.java.model.MinecraftProfile;
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode;
import net.raphimc.minecraftauth.msa.service.impl.DeviceCodeMsaAuthService;
import net.raphimc.minecraftauth.util.jwt.Jwt;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * ReauthService
 *
 * Wrapper for the MinecraftAuth library. Everything is a completablefuture since
 * we have to run the network threads on a separate executor to avoid blocking the main thread.
 */
public final class ReauthService {

    private static final String USER_AGENT = "AuthAgain/1.0.0";
    private static final HttpClient CLIENT = MinecraftAuth.createHttpClient(USER_AGENT);

    private static final String PROFILE_ENDPOINT = "https://api.minecraftservices.com/minecraft/profile";
    private static final java.net.http.HttpClient JAVA_HTTP = java.net.http.HttpClient.newHttpClient();

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "AuthAgain-Auth");
        thread.setDaemon(true);
        return thread;
    });

    public enum TokenStatus {
        VALID, INVALID, UNKNOWN
    }

    private ReauthService() {
    }

    /**
     * Checks whether an access token still authenticates by calling the profile
     * endpoint. Works for launcher-imported accounts that have no refresh token.
     * <p>
     * @return VALID on HTTP 200, INVALID on 401/403, UNKNOWN otherwise.
     */
    public static CompletableFuture<TokenStatus> validate(String accessToken) {
        return CompletableFuture.supplyAsync(() -> {
            if (accessToken == null || accessToken.isBlank()) {
                return TokenStatus.INVALID;
            }
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(PROFILE_ENDPOINT))
                        .timeout(Duration.ofSeconds(10))
                        .header("Authorization", "Bearer " + accessToken)
                        .GET()
                        .build();
                HttpResponse<Void> response = JAVA_HTTP.send(request, HttpResponse.BodyHandlers.discarding());
                int code = response.statusCode();
                if (code == 200) {
                    return TokenStatus.VALID;
                }
                if (code == 401 || code == 403) {
                    return TokenStatus.INVALID;
                }
                AuthAgainMod.LOGGER.warn("[AuthAgain] Unexpected status {} while validating a token.", code);
                return TokenStatus.UNKNOWN;
            } catch (Exception e) {
                AuthAgainMod.LOGGER.warn("[AuthAgain] Token validation request failed.", e);
                return TokenStatus.UNKNOWN;
            }
        }, EXECUTOR);
    }

    /**
     * Flow for adding a new account
     * <p>
     * @return CompletableFuture which completes with a logged-in {@link JavaAuthManager} once the user finishes in the browser, or with an exception on failure/timeout.
	 * @throws CompletionException if the login fails or times out.
	 * @implNote Caller needs to handle deduping accounts so we do not create multiple of the same account. This method does not check if the account already exists.
     */
    public static CompletableFuture<JavaAuthManager> startDeviceCodeLogin(Consumer<DeviceCodePrompt> onPrompt) {
        return CompletableFuture.supplyAsync(() -> {
            Consumer<MsaDeviceCode> prompt = code -> onPrompt.accept(DeviceCodePrompt.from(code));
            try {
                JavaAuthManager authManager = JavaAuthManager.create(CLIENT)
                        .login(DeviceCodeMsaAuthService::new, prompt);
                // Populate token and profile so toAccount() can read the without
                // getting null.
                authManager.getMinecraftToken().getUpToDate();
                authManager.getMinecraftProfile().getUpToDate();
                return authManager;
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, EXECUTOR);
    }

    /**
     * Refreshes existing account silently
     * <p>
	 * @return CompletableFuture which completes with a logged-in {@link JavaAuthManager} if the refresh succeeds, or with an exception if the refresh fails.
     * @implNote This method does not check if the refresh token is still valid.
     * @throws CompletionException if the refresh token is no longer valid. Caller should fall back to {@link #startDeviceCodeLogin} in this case.
     */
    public static CompletableFuture<JavaAuthManager> silentRefresh(JsonObject sessionJson) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JavaAuthManager authManager = JavaAuthManager.fromJson(CLIENT, sessionJson);
                authManager.getMinecraftToken().getUpToDate();
                authManager.getMinecraftProfile().getUpToDate();
                return authManager;
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, EXECUTOR);
    }

    /**
     * Serialize session to JSON for storage.
     */
    public static JsonObject serialize(JavaAuthManager authManager) {
        return JavaAuthManager.toJson(authManager);
    }

    /**
     * Builds a brand-new {@link AuthAccount} from a freshly logged-in manager.
     * <p>
     * Reads the cached profile/token, so callers must have finished a login or a
     * {@link #silentRefresh} (both of which populate the cache) beforehand.
     */
    public static AuthAccount toAccount(JavaAuthManager authManager) {
        MinecraftProfile profile = authManager.getMinecraftProfile().getCached();
        return new AuthAccount(profile.getName(), profile.getId(), extractXuid(authManager), serialize(authManager));
    }

    /**
     * Returns a copy of {@code existing} carrying the refreshed session, so a
     * reauth updates the stored account in place without changing its local id.
     */
    public static AuthAccount toAccount(AuthAccount existing, JavaAuthManager authManager) {
        MinecraftProfile profile = authManager.getMinecraftProfile().getCached();
        return existing.withSession(profile.getName(), extractXuid(authManager), serialize(authManager), System.currentTimeMillis());
    }

    /**
     * Reads the XUID from the Minecraft access token JWT.
     * <p>
     * The Java flow does not expose the XUID, so we decode the
     * {@code xuid} claim from the access token instead. Returns an empty string
     * if the token is missing the claim.
     * <p>
     * This reads the cached token, so callers must ensure the Minecraft token is up to
     * date first.
     */
    public static String extractXuid(JavaAuthManager authManager) {
        var payload = Jwt.parse(authManager.getMinecraftToken().getCached().getToken()).getPayload();
        return payload.hasString("xuid") ? payload.reqString("xuid") : "";
    }
}
