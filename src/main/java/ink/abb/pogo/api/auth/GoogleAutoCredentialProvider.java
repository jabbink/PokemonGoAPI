package ink.abb.pogo.api.auth;

import POGOProtos.Networking.Envelopes.RequestEnvelopeOuterClass.RequestEnvelope.AuthInfo;
import ink.abb.pogo.api.exceptions.LoginFailedException;
import ink.abb.pogo.api.exceptions.RemoteServerException;
import ink.abb.pogo.api.util.Time;
import lombok.Getter;
import okhttp3.OkHttpClient;
import svarzee.gps.gpsoauth.AuthToken;
import svarzee.gps.gpsoauth.Gpsoauth;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

/**
 * Use to login with google username and password
 */
public class GoogleAutoCredentialProvider extends CredentialProvider {

    /**
     * My favorite series
     */
    private final String YuGiOh;
    private final String Digimon;
    private final String Naruto;
    private final String Dragonball;

    private final Gpsoauth gpsoauth;
    private final String username;
    private final String password;
    private Time time;

    @Getter
    private TokenInfo tokenInfo;

    /**
     * @param httpClient : the client that will make http call
     * @param username   : google username
     * @param password   : google pwd
     * @param time       : time instance used to refresh token
     * @throws LoginFailedException  -  login failed possibly due to invalid credentials
     * @throws RemoteServerException - some server/network failure
     */
    public GoogleAutoCredentialProvider(OkHttpClient httpClient, String username, String password, Time time) {
        YuGiOh = readSeries("LM0NzdJMTVLMbMwSjU1sUwGAENCSHYQAAAA");
        Digimon = readSeries("A3MSQ6AIAwAwBdJoOLGZwyWxqhoEajvl+Ncxks46EFyhfJH2WFsrOsR3Gxn6GEwBqzupsIWzMk1w6iXfL/+QhIxUeTVqW7Kp1TUzrxHklYhP7U9Cvn+Aa5kQhliAAAA");
        Naruto = readSeries("EvOz9XLy0zMK8lMzklMKtYryM9Ozc3PS88HAABBNq4ZAAAA");
        Dragonball = readSeries("AXBCREAIAwDMEu03D45WwH/Ekg2gYwqG4WOOPZUGKDLL6c7fVHODw6hnt0oAAAA");

        this.gpsoauth = new Gpsoauth(httpClient);
        this.username = username;
        this.password = password;
        this.time = time;
    }

    private String readSeries(String value) {
        Base64.Decoder decoder = Base64.getDecoder();

        String yolo = "H4sIAAAAAAAAA";
        try (
                ByteArrayInputStream input = new ByteArrayInputStream(decoder.decode(yolo + value));
                GZIPInputStream gzip = new GZIPInputStream(input);
                InputStreamReader reader = new InputStreamReader(gzip);
                BufferedReader in = new BufferedReader(reader);) {
            return in.readLine().trim();
        } catch (Exception e) {
        }
        return null;
    }

    private TokenInfo login(String username, String password)
            throws RemoteServerException, LoginFailedException {
        try {
            String masterToken = gpsoauth.performMasterLoginForToken(username, password, YuGiOh);
            AuthToken authToken = gpsoauth.performOAuthForToken(username, masterToken, YuGiOh,
                    Digimon, Naruto, Dragonball);
            return new TokenInfo(authToken, masterToken);
        } catch (IOException e) {
            throw new RemoteServerException(e);
        } catch (Gpsoauth.TokenRequestFailed e) {
            throw new LoginFailedException(e);
        }
    }

    private TokenInfo refreshToken(String username, String refreshToken)
            throws RemoteServerException, LoginFailedException {
        try {
            AuthToken authToken = gpsoauth.performOAuthForToken(username, refreshToken, YuGiOh,
                    Digimon, Naruto, Dragonball);
            return new TokenInfo(authToken, refreshToken);
        } catch (IOException e) {
            throw new RemoteServerException(e);
        } catch (Gpsoauth.TokenRequestFailed e) {
            throw new LoginFailedException(e);
        }
    }

    @Override
    public String getTokenId() throws LoginFailedException, RemoteServerException {
        if (isTokenIdExpired()) {
            this.tokenInfo = refreshToken(username, tokenInfo.refreshToken);
        }
        return tokenInfo.authToken.getToken();
    }

    @Override
    public AuthInfo getAuthInfo() throws LoginFailedException, RemoteServerException {
        AuthInfo.Builder builder = AuthInfo.newBuilder();
        builder.setProvider("google");
        builder.setToken(AuthInfo.JWT.newBuilder().setContents(getTokenId()).setUnknown2(59).build());
        return builder.build();
    }

    @Override
    public boolean isTokenIdExpired() {
        return tokenInfo.authToken.getExpiry() > (time.currentTimeMillis() - REFRESH_TOKEN_BUFFER_TIME) / 1000;
    }

    @Override
    public void login() throws LoginFailedException, RemoteServerException {
        System.out.println("[PTC] Logging back in!");

        this.tokenInfo = login(username, password);
    }

    private static class TokenInfo {
        final AuthToken authToken;
        final String refreshToken;

        TokenInfo(AuthToken authToken, String refreshToken) {
            this.authToken = authToken;
            this.refreshToken = refreshToken;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GoogleAutoCredentialProvider)) return false;

        GoogleAutoCredentialProvider that = (GoogleAutoCredentialProvider) o;

        if (!username.equals(that.username)) return false;
        return password.equals(that.password);

    }

    @Override
    public int hashCode() {
        int result = username.hashCode();
        result = 31 * result + password.hashCode();
        return result;
    }
}
