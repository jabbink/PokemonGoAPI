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
import ink.abb.pogo.api.exceptions.LoginFailedException;
import ink.abb.pogo.api.exceptions.RemoteServerException;

import java.util.concurrent.TimeUnit;

/**
 * Any Credential Provider can extend this.
 */
public abstract class CredentialProvider {

    public static final long REFRESH_TOKEN_BUFFER_TIME = TimeUnit.MINUTES.toMillis(5);

    public abstract String getTokenId() throws LoginFailedException, RemoteServerException;

    public abstract AuthInfo getAuthInfo() throws LoginFailedException, RemoteServerException;

    public abstract boolean isTokenIdExpired();

    public abstract void login() throws LoginFailedException, RemoteServerException;
}
