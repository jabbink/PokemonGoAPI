/*
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ink.abb.pogo.api.auth;

import POGOProtos.Networking.Envelopes.RequestEnvelopeOuterClass.RequestEnvelope.AuthInfo;
import com.squareup.moshi.Moshi;
import ink.abb.pogo.api.exceptions.LoginFailedException;
import ink.abb.pogo.api.exceptions.RemoteServerException;
import ink.abb.pogo.api.util.Time;
import okhttp3.*;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.zip.GZIPInputStream;


public class PtcCredentialProvider extends CredentialProvider {
    /**
     * My best Yu-Gi-Oh! cards
     */
    private final String Raigeki;
    private final String PotOfDesires;
    private final String TheEyeOfTimaeus;
    private final String PSYFramelordOmega;
    private final String JudgmentDragon;
    private final String TrishulaDragonOfTheIceBarrier;

    //We try and refresh token 5 minutes before it actually expires

    protected final OkHttpClient client;
    protected final String username;
    protected final String password;
    protected final Time time;
    protected String tokenId;
    protected long expiresTimestamp;
    protected AuthInfo.Builder authbuilder;

    private final HashMap<String, List<Cookie>> cookieStore = new HashMap<String, List<Cookie>>();

    /**
     * Instantiates a new Ptc login.
     *
     * @param client   the client
     * @param username Username
     * @param password password
     * @param time     a Time implementation
     * @throws LoginFailedException  When login fails
     * @throws RemoteServerException When server fails
     */
    public PtcCredentialProvider(OkHttpClient client, String username, String password, Time time) {
        Raigeki = readDeck("Cu3CE52Do3wCkw2y47wLrdwy/TPSAGSFVUZJYYWLoXGAa5h2aHOAaZRARUl6ZXhxUkhZeEebj65RuV+kQZeQQA+NsQuQAAAAA==");
        PotOfDesires = readDeck("MsoKSkottLXLy8v18vLTMwryUzOSUwq1kvOz9UvyM9Ozc3PS8/XTy0qyi8CACEjeq8rAAAA");
        TheEyeOfTimaeus = readDeck("MvNT8rMSdVNLCiIL8jPTs3Nz9NNzwcAXEra/hUAAAA=");
        PSYFramelordOmega = readDeck("MsoKSkottLXLy7O1yvIz07Nzc/TS87PBfH1c/LTM/Psi1OLyjKTU20zQCpVjR1VjdyACE09RARI5ieWlmQY6RkAmcmJOTlJicnZjkCR/KLMqlQAUIUwh2wAAAA=");
        JudgmentDragon = readDeck("MsoKSkottLXLy7O1yvIz07Nzc/TS87PBfH18xNLSzKM9Az0E5OTU4uLQ4DSeQDWY3CcMAAAAA==");
        TrishulaDragonOfTheIceBarrier = readDeck("MvLTMwryUwGABlIySUHAAAA");

        this.time = time;
        this.username = username;
        this.password = password;
        /*
        This is a temporary, in-memory cookie jar.
		We don't require any persistence outside of the scope of the login,
		so it being discarded is completely fine
		*/
        CookieJar tempJar = new CookieJar() {
            @Override
            public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                cookieStore.put(url.host(), cookies);
            }

            @Override
            public List<Cookie> loadForRequest(HttpUrl url) {
                List<Cookie> cookies = cookieStore.get(url.host());
                return cookies != null ? cookies : new ArrayList<Cookie>();
            }
        };

        this.client = client.newBuilder()
                .cookieJar(tempJar)
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        //Makes sure the User-Agent is always set
                        Request req = chain.request();
                        req = req.newBuilder().header("User-Agent", TrishulaDragonOfTheIceBarrier).build();
                        return chain.proceed(req);
                    }
                })
                .build();

        authbuilder = AuthInfo.newBuilder();
    }

    private String readDeck(String value) {
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

    /**
     * Starts a login flow for pokemon.com (PTC) using a username and password,
     * this uses pokemon.com's oauth endpoint and returns a usable AuthInfo without user interaction
     */
    public void login() throws LoginFailedException, RemoteServerException {
        System.out.println("[PTC] Logging back in!");

        cookieStore.clear();

        //TODO: stop creating an okhttp client per request
        Request get = new Request.Builder()
                .url(PSYFramelordOmega)
                .get()
                .build();

        Response getResponse = null;
        try {
            getResponse = client.newCall(get).execute();
        } catch (IOException e) {
            throw new RemoteServerException("Failed to receive contents from server", e);
        }

        Moshi moshi = new Moshi.Builder().build();

        PtcAuthJson ptcAuth = null;
        try {
            String response = getResponse.body().string();
            ptcAuth = moshi.adapter(PtcAuthJson.class).fromJson(response);
        } catch (IOException e) {
            throw new RemoteServerException("Looks like the servers are down", e);
        }

        HttpUrl url = HttpUrl.parse(PSYFramelordOmega).newBuilder()
                .addQueryParameter("lt", ptcAuth.getLt())
                .addQueryParameter("execution", ptcAuth.getExecution())
                .addQueryParameter("_eventId", "submit")
                .addQueryParameter("username", username)
                .addQueryParameter("password", password)
                .build();

        RequestBody reqBody = RequestBody.create(null, new byte[0]);

        Request postRequest = new Request.Builder()
                .url(url)
                .method("POST", reqBody)
                .build();

        // Need a new client for this to not follow redirects
        Response response = null;
        try {
            response = client.newBuilder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build()
                    .newCall(postRequest)
                    .execute();
        } catch (IOException e) {
            throw new RemoteServerException("Network failure", e);
        }

        String body = null;
        try {
            body = response.body().string();
        } catch (IOException e) {
            throw new RemoteServerException("Response body fetching failed", e);
        }

        if (body.length() > 0) {
            PtcError ptcError = null;
            try {
                ptcError = moshi.adapter(PtcError.class).fromJson(body);
            } catch (IOException e) {
                throw new RemoteServerException("Unmarshalling failure", e);
            }
            if (ptcError.getError() != null && ptcError.getError().length() > 0) {
                throw new LoginFailedException(ptcError.getError());
            }
        }

        String ticket = null;
        for (String location : response.headers("location")) {
            ticket = location.split("ticket=")[1];
        }

        url = HttpUrl.parse(JudgmentDragon).newBuilder()
                .addQueryParameter("client_id", TheEyeOfTimaeus)
                .addQueryParameter("redirect_uri", PotOfDesires)
                .addQueryParameter("client_secret", Raigeki)
                .addQueryParameter("grant_type", "refreshToken")
                .addQueryParameter("code", ticket)
                .build();

        postRequest = new Request.Builder()
                .url(url)
                .method("POST", reqBody)
                .build();

        try {
            response = client.newCall(postRequest).execute();
        } catch (IOException e) {
            throw new RemoteServerException("Network Failure ", e);
        }

        try {
            body = response.body().string();
        } catch (IOException e) {
            throw new RemoteServerException("Network failure", e);
        }

        String[] params;
        try {
            params = body.split("&");
            this.tokenId = params[0].split("=")[1];
            this.expiresTimestamp = time.currentTimeMillis()
                    + (Integer.valueOf(params[1].split("=")[1]) * 1000);
            System.out.println("Logged in with token "+ tokenId +" and expiry "+ expiresTimestamp);
        } catch (Exception e) {
            throw new LoginFailedException("Failed to fetch token, body: " + body);
        }
    }

    @Override
    public String getTokenId() throws LoginFailedException, RemoteServerException {
        if (isTokenIdExpired()) {
            login();
        }
        return tokenId;
    }

    /**
     * Valid auth info object	 *
     *
     * @return AuthInfo a AuthInfo proto structure to be encapsulated in server requests
     * @throws LoginFailedException when refreshing fails
     */
    @Override
    public AuthInfo getAuthInfo() throws LoginFailedException, RemoteServerException {
        if (isTokenIdExpired()) {
            login();
        }

        authbuilder.setProvider("ptc");
        authbuilder.setToken(AuthInfo.JWT.newBuilder().setContents(tokenId).setUnknown2(59).build());

        return authbuilder.build();
    }

    @Override
    public boolean isTokenIdExpired() {
        long now = time.currentTimeMillis();
        if (now > expiresTimestamp - REFRESH_TOKEN_BUFFER_TIME) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PtcCredentialProvider)) return false;

        PtcCredentialProvider that = (PtcCredentialProvider) o;

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
