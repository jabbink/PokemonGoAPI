package ink.abb.pogo.api.util;

import POGOProtos.Networking.Envelopes.AuthTicketOuterClass;
import POGOProtos.Networking.Envelopes.RequestEnvelopeOuterClass;
import POGOProtos.Networking.Envelopes.SignatureOuterClass;
import POGOProtos.Networking.Platform.PlatformRequestTypeOuterClass;
import POGOProtos.Networking.Platform.Requests.SendEncryptedSignatureRequestOuterClass;
import POGOProtos.Networking.Requests.RequestOuterClass;
import com.google.protobuf.ByteString;
import ink.abb.pogo.api.PoGoApi;
import net.jpountz.xxhash.StreamingXXHash32;
import net.jpountz.xxhash.StreamingXXHash64;
import net.jpountz.xxhash.XXHashFactory;

import java.util.Random;

public class Signature {

    /**
     * Given a fully built request, set the signature correctly.
     *
     * @param poGoApi the api
     * @param builder the requestenvelop builder
     */
    public static void setSignature(PoGoApi poGoApi, RequestEnvelopeOuterClass.RequestEnvelope.Builder builder, Long lastRequest) {
        long curTime = poGoApi.currentTimeMillis();
        long timestampSinceStart = curTime - poGoApi.getStartTime();

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
        System.out.println("Sending " + locationFixCount + " location fixes");

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

            sigBuilder.addLocationFix(locationFix.build());
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
                .setRotationVectorX(-55.0 + sRandom.nextDouble() * 110.0)
                .setRotationVectorY(-55.0 + sRandom.nextDouble() * 110.0)
                .setRotationVectorZ(-55.0 + sRandom.nextDouble() * 110.0)
                .setLinearAccelerationX(0.1 + (0.7 - 0.1) * sRandom.nextDouble())
                .setLinearAccelerationY(0.1 + (0.8 - 0.1) * sRandom.nextDouble())
                .setLinearAccelerationZ(0.1 + (0.8 - 0.1) * sRandom.nextDouble())
                .setGyroscopeRawX(-1.0 + sRandom.nextDouble() * 2.0)
                .setGyroscopeRawY(-1.0 + sRandom.nextDouble() * 2.0)
                .setGyroscopeRawZ(-1.0 + sRandom.nextDouble() * 2.0)
                .setGravityX(-1.0 + sRandom.nextDouble() * 2.0)
                .setGravityY(6.0 + (9.0 - 6.0) * sRandom.nextDouble())
                // wat? Copied from Grover-C13 repo, makes no sense tbh
                .setGravityZ(-1.0 + (8.0 - (-1.0)) * sRandom.nextDouble())
                .setAccelerometerAxes(3);

        sigBuilder.setSensorInfo(sensorInfo);


        if (authTicketBA != null) {
            for (RequestOuterClass.Request serverRequest : builder.getRequestsList()) {
                byte[] request = serverRequest.toByteArray();
                sigBuilder.addRequestHash(getRequestHash(authTicketBA, request));
            }
        }

        byte[] uk2 = sigBuilder.build().toByteArray();
        byte[] encrypted = Crypto43.encrypt(uk2, timestampSinceStart).toByteBuffer().array();
        RequestEnvelopeOuterClass.RequestEnvelope.PlatformRequest platformRequest = RequestEnvelopeOuterClass.RequestEnvelope.PlatformRequest.newBuilder()
                .setType(PlatformRequestTypeOuterClass.PlatformRequestType.SEND_ENCRYPTED_SIGNATURE)
                .setRequestMessage(SendEncryptedSignatureRequestOuterClass.SendEncryptedSignatureRequest.newBuilder().setEncryptedSignature(ByteString.copyFrom(encrypted)).build().toByteString()).build();
        builder.addPlatformRequests(platformRequest);
    }

    private static byte[] getBytes(double input) {
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


    private static int getLocationHash1(byte[] authTicket,
                                        RequestEnvelopeOuterClass.RequestEnvelope.Builder builder) {
        XXHashFactory factory = XXHashFactory.fastestInstance();
        StreamingXXHash32 xx32 = factory.newStreamingHash32(Constants.HASH_SEED);
        xx32.update(authTicket, 0, authTicket.length);
        byte[] bytes = new byte[8 * 3];

        System.arraycopy(getBytes(builder.getLatitude()), 0, bytes, 0, 8);
        System.arraycopy(getBytes(builder.getLongitude()), 0, bytes, 8, 8);
        System.arraycopy(getBytes(builder.getAccuracy()), 0, bytes, 16, 8);

        xx32 = factory.newStreamingHash32(xx32.getValue());
        xx32.update(bytes, 0, bytes.length);
        return xx32.getValue();
    }

    private static int getLocationHash2(RequestEnvelopeOuterClass.RequestEnvelope.Builder builder) {
        XXHashFactory factory = XXHashFactory.fastestInstance();
        byte[] bytes = new byte[8 * 3];

        System.arraycopy(getBytes(builder.getLatitude()), 0, bytes, 0, 8);
        System.arraycopy(getBytes(builder.getLongitude()), 0, bytes, 8, 8);
        System.arraycopy(getBytes(builder.getAccuracy()), 0, bytes, 16, 8);

        StreamingXXHash32 xx32 = factory.newStreamingHash32(Constants.HASH_SEED);
        xx32.update(bytes, 0, bytes.length);

        return xx32.getValue();
    }

    private static long getRequestHash(byte[] authTicket, byte[] request) {
        XXHashFactory factory = XXHashFactory.fastestInstance();
        StreamingXXHash64 xx64 = factory.newStreamingHash64(Constants.HASH_SEED);
        xx64.update(authTicket, 0, authTicket.length);
        xx64 = factory.newStreamingHash64(xx64.getValue());
        xx64.update(request, 0, request.length);
        return xx64.getValue();
    }

    private static float offsetOnLatLong(double locationParam, double ran) {
        double round = 6378137;
        double dl = ran / (round * Math.cos(Math.PI * locationParam / 180));
        return (float) (locationParam + dl * 180 / Math.PI);
    }
}
