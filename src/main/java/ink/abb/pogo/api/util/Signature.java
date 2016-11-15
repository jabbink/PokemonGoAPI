package ink.abb.pogo.api.util;

import POGOProtos.Networking.Envelopes.AuthTicketOuterClass;
import POGOProtos.Networking.Envelopes.RequestEnvelopeOuterClass;
import POGOProtos.Networking.Envelopes.SignatureOuterClass;
import POGOProtos.Networking.Platform.PlatformRequestTypeOuterClass;
import POGOProtos.Networking.Platform.Requests.SendEncryptedSignatureRequestOuterClass;
import POGOProtos.Networking.Requests.RequestOuterClass;
import com.google.protobuf.ByteString;
import ink.abb.pogo.api.PoGoApi;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

public class Signature {
    Crypto43 crypto43 = new Crypto43();
    PoGoApi poGoApi;

    private long lastTimestampSinceStart = 0;
    private long lastCurTime = 0;
    private SignatureOuterClass.Signature.SensorInfo.Builder lastSensorInfo;
    private ArrayList<SignatureOuterClass.Signature.LocationFix> lastLocationFixes;

    public Signature(PoGoApi poGoApi) {
        this.poGoApi = poGoApi;
    }

    /**
     * Given a fully built request, set the signature correctly.
     *
     * @param builder the requestenvelop builder
     */
    public void setSignature(RequestEnvelopeOuterClass.RequestEnvelope.Builder builder, Long lastRequest) {
        long curTime = poGoApi.currentTimeMillis();
        long timestampSinceStart = curTime - poGoApi.getStartTime();

        lastTimestampSinceStart = timestampSinceStart;
        lastCurTime = curTime;

        SignatureOuterClass.Signature.Builder sigBuilder = SignatureOuterClass.Signature.newBuilder()
                .setLocationHash2(getLocationHash2(builder))
                .setSessionHash(ByteString.copyFrom(poGoApi.getSessionHash()))
                .setTimestamp(poGoApi.currentTimeMillis())
                .setTimestampSinceStart(timestampSinceStart);

        AuthTicketOuterClass.AuthTicket authTicket = builder.getAuthTicket();
        byte[] authTicketBA;
        if (authTicket != null) {
            authTicketBA = builder.getAuthTicket().toByteArray();
        } else {
            authTicketBA = builder.getAuthInfo().toByteArray();
        }

        if (authTicketBA != null) {
            sigBuilder.setLocationHash1(getLocationHash1(authTicketBA, builder));
        }


        SignatureOuterClass.Signature.DeviceInfo deviceInfo = poGoApi.getDeviceInfo();
        if (deviceInfo != null) {
            sigBuilder.setDeviceInfo(deviceInfo);
        }

        // ~1 locationfix per 1.5 sec

        int locationFixCount = 1;
        double diff = (Math.random() * 500.0 + 1250.0);
        if (curTime - lastRequest > 1000) {
            locationFixCount = (int) Math.ceil((curTime - lastRequest) / diff);
        }
        //System.out.println("Sending " + locationFixCount + " location fixes");

        lastLocationFixes = new ArrayList<>();
        for (int i = 0; i < locationFixCount; i++) {
            double lat = poGoApi.getLatitude();
            double lng = poGoApi.getLongitude();
            if (i < locationFixCount - 1) {
                lat = offsetOnLatLong(lat, Math.random() * 100.0 + 10.0);
                lng = offsetOnLatLong(lng, Math.random() * 100.0 + 10.0);
            }
            SignatureOuterClass.Signature.LocationFix.Builder locationFix =
                    SignatureOuterClass.Signature.LocationFix.newBuilder()
                            .setProvider("fused")
                            .setTimestampSnapshot(Math.round((locationFixCount - i) * diff) - (long) (Math.random() * 300))
                            .setLatitude((float) lat)
                            .setLongitude((float) lng)
                            .setAltitude((float) (poGoApi.getAltitude() + Math.random() - 0.5))
                            .setHorizontalAccuracy(-1)
                            .setVerticalAccuracy((float) (15 + (23 - 15) * Math.random()))
                            .setProviderStatus(3)
                            .setLocationType(1);

            builder.setMsSinceLastLocationfix(locationFix.getTimestampSnapshot());

            SignatureOuterClass.Signature.LocationFix fix = locationFix.build();

            sigBuilder.addLocationFix(fix);
            lastLocationFixes.add(fix);
        }

        /*SignatureOuterClass.Signature.ActivityStatus.Builder activityStatus = SignatureOuterClass.Signature.ActivityStatus.newBuilder()
                .setUnknownStatus(true)
                .setWalking(true)
                .setStationary(true)
                .setAutomotive(true)
                .setTilting(true);

        sigBuilder.setActivityStatus(activityStatus.build());*/

        /**
         * TODO:
         * 0.33.0 = 2016080700
         * 0.35.0 = 2016082200
         *  xxHash64("\""+ sha1(value_from_above) +"\"".ToByteArray(), 0x88533787)
         */

        sigBuilder.setUnknown25(Constants.UNKNOWN_25);

        Random sRandom = new Random();

        SignatureOuterClass.Signature.SensorInfo.Builder sensorInfo = SignatureOuterClass.Signature.SensorInfo.newBuilder()
                .setTimestampSnapshot(Math.max(timestampSinceStart - (long) (Math.random() * 300), 0))
                .setMagneticFieldX(-0.7 + sRandom.nextDouble() * 1.4)
                .setMagneticFieldY(-0.7 + sRandom.nextDouble() * 1.4)
                .setMagneticFieldZ(-0.7 + sRandom.nextDouble() * 1.4)
                .setRotationRateX(-55.0 + sRandom.nextDouble() * 110.0)
                .setRotationRateY(-55.0 + sRandom.nextDouble() * 110.0)
                .setRotationRateZ(-55.0 + sRandom.nextDouble() * 110.0)
                .setLinearAccelerationX(0.1 + (0.7 - 0.1) * sRandom.nextDouble())
                .setLinearAccelerationY(0.1 + (0.8 - 0.1) * sRandom.nextDouble())
                .setLinearAccelerationZ(0.1 + (0.8 - 0.1) * sRandom.nextDouble())
                .setAttitudePitch(-1.0 + sRandom.nextDouble() * 2.0)
                .setAttitudeRoll(-1.0 + sRandom.nextDouble() * 2.0)
                .setAttitudeYaw(-1.0 + sRandom.nextDouble() * 2.0)
                .setGravityX(-1.0 + sRandom.nextDouble() * 2.0)
                .setGravityY(6.0 + (9.0 - 6.0) * sRandom.nextDouble())
                // wat? Copied from Grover-C13 repo, makes no sense tbh
                .setGravityZ(-1.0 + (8.0 - (-1.0)) * sRandom.nextDouble())
                .setStatus(3);

        lastSensorInfo = sensorInfo;

        sigBuilder.addSensorInfo(sensorInfo);


        if (authTicketBA != null) {
            for (RequestOuterClass.Request serverRequest : builder.getRequestsList()) {
                byte[] request = serverRequest.toByteArray();
                sigBuilder.addRequestHash(getRequestHash(authTicketBA, request));
            }
        }

        byte[] uk2 = sigBuilder.build().toByteArray();
        byte[] encrypted = crypto43.encrypt(uk2, timestampSinceStart).array();
        RequestEnvelopeOuterClass.RequestEnvelope.PlatformRequest platformRequest = RequestEnvelopeOuterClass.RequestEnvelope.PlatformRequest.newBuilder()
                .setType(PlatformRequestTypeOuterClass.PlatformRequestType.SEND_ENCRYPTED_SIGNATURE)
                .setRequestMessage(SendEncryptedSignatureRequestOuterClass.SendEncryptedSignatureRequest.newBuilder().setEncryptedSignature(ByteString.copyFrom(encrypted)).build().toByteString()).build();
        builder.addPlatformRequests(platformRequest);
    }

    private byte[] getBytes(double input) {
        long rawDouble = Double.doubleToRawLongBits(input);
        return new byte[]{
                (byte) (rawDouble >>> 56),
                (byte) (rawDouble >>> 48),
                (byte) (rawDouble >>> 40),
                (byte) (rawDouble >>> 32),
                (byte) (rawDouble >>> 24),
                (byte) (rawDouble >>> 16),
                (byte) (rawDouble >>> 8),
                (byte) rawDouble
        };
    }

    public boolean verifySignature(RequestEnvelopeOuterClass.RequestEnvelope.Builder builder, Long lastRequest) {
        SignatureOuterClass.Signature.Builder sigBuilder = SignatureOuterClass.Signature.newBuilder()
                .setLocationHash2(getLocationHash2(builder))
                .setSessionHash(ByteString.copyFrom(poGoApi.getSessionHash()))
                .setTimestamp(lastCurTime)
                .setTimestampSinceStart(lastTimestampSinceStart);
        AuthTicketOuterClass.AuthTicket authTicket = builder.getAuthTicket();

        byte[] authTicketBA;
        if (authTicket != null) {
            authTicketBA = builder.getAuthTicket().toByteArray();
        } else {
            authTicketBA = builder.getAuthInfo().toByteArray();
        }

        SignatureOuterClass.Signature.DeviceInfo deviceInfo = poGoApi.getDeviceInfo();
        if (deviceInfo != null) {
            sigBuilder.setDeviceInfo(deviceInfo);
        }
        System.out.println("device info == "+ deviceInfo);

        System.out.println("authTicketBA == "+ Arrays.toString(authTicketBA));

        sigBuilder.setUnknown25(Constants.UNKNOWN_25);

        if (authTicketBA != null) {
            for (RequestOuterClass.Request serverRequest : builder.getRequestsList()) {
                byte[] request = serverRequest.toByteArray();
                sigBuilder.addRequestHash(getRequestHash(authTicketBA, request));
            }

            sigBuilder.setLocationHash1(getLocationHash1(authTicketBA, builder));
        }

        for (SignatureOuterClass.Signature.LocationFix fix : lastLocationFixes) {
            sigBuilder.addLocationFix(fix);
        }

        sigBuilder.addSensorInfo(lastSensorInfo);
        byte[] uk2 = sigBuilder.build().toByteArray();
        byte[] encrypted = crypto43.encrypt(uk2, lastTimestampSinceStart).array();
        RequestEnvelopeOuterClass.RequestEnvelope.PlatformRequest platformRequest = RequestEnvelopeOuterClass.RequestEnvelope.PlatformRequest.newBuilder()
                .setType(PlatformRequestTypeOuterClass.PlatformRequestType.SEND_ENCRYPTED_SIGNATURE)
                .setRequestMessage(SendEncryptedSignatureRequestOuterClass.SendEncryptedSignatureRequest.newBuilder().setEncryptedSignature(ByteString.copyFrom(encrypted)).build().toByteString()).build();
        return Arrays.equals(builder.getPlatformRequests(0).toByteArray(), platformRequest.toByteArray());
    }

    private int getLocationHash1(byte[] authTicket,
                                        RequestEnvelopeOuterClass.RequestEnvelope.Builder builder) {
        int seed = Hasher.hash32(authTicket);
        byte[] bytes = new byte[8 * 3];

        System.arraycopy(getBytes(builder.getLatitude()), 0, bytes, 0, 8);
        System.arraycopy(getBytes(builder.getLongitude()), 0, bytes, 8, 8);
        System.arraycopy(getBytes(builder.getAccuracy()), 0, bytes, 16, 8);

        return Hasher.hash32Salt(bytes, Hasher.toBytes(seed));
    }

    private int getLocationHash2(RequestEnvelopeOuterClass.RequestEnvelope.Builder builder) {
        byte[] bytes = new byte[8 * 3];

        System.arraycopy(getBytes(builder.getLatitude()), 0, bytes, 0, 8);
        System.arraycopy(getBytes(builder.getLongitude()), 0, bytes, 8, 8);
        System.arraycopy(getBytes(builder.getAccuracy()), 0, bytes, 16, 8);

        return Hasher.hash32(bytes);
    }

    private long getRequestHash(byte[] authTicket, byte[] request) {
        byte[] seed = ByteBuffer.allocate(8).putLong(Hasher.hash64(authTicket)).array();
        return Hasher.hash64Salt(request, seed);
    }

    private float offsetOnLatLong(double locationParam, double ran) {
        double round = 6378137;
        double dl = ran / (round * Math.cos(Math.PI * locationParam / 180));
        return (float) (locationParam + dl * 180 / Math.PI);
    }
}
