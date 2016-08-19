package ink.abb.pogo.api.util

import POGOProtos.Networking.Envelopes.SignatureOuterClass
import ink.abb.pogo.api.toHexString
import java.util.*

class DeviceInfoGenerator {
    companion object {

        val devices = arrayOf(
                Triple("iPad3,1", "iPad", "J1AP"),
                Triple("iPad3,2", "iPad", "J2AP"),
                Triple("iPad3,3", "iPad", "J2AAP"),
                Triple("iPad3,4", "iPad", "P101AP"),
                Triple("iPad3,5", "iPad", "P102AP"),
                Triple("iPad3,6", "iPad", "P103AP"),

                Triple("iPad4,1", "iPad", "J71AP"),
                Triple("iPad4,2", "iPad", "J72AP"),
                Triple("iPad4,3", "iPad", "J73AP"),
                Triple("iPad4,4", "iPad", "J85AP"),
                Triple("iPad4,5", "iPad", "J86AP"),
                Triple("iPad4,6", "iPad", "J87AP"),
                Triple("iPad4,7", "iPad", "J85mAP"),
                Triple("iPad4,8", "iPad", "J86mAP"),
                Triple("iPad4,9", "iPad", "J87mAP"),

                Triple("iPad5,1", "iPad", "J96AP"),
                Triple("iPad5,2", "iPad", "J97AP"),
                Triple("iPad5,3", "iPad", "J81AP"),
                Triple("iPad5,4", "iPad", "J82AP"),

                Triple("iPad6,7", "iPad", "J98aAP"),
                Triple("iPad6,8", "iPad", "J99aAP"),

                Triple("iPhone5,1", "iPhone", "N41AP"),
                Triple("iPhone5,2", "iPhone", "N42AP"),
                Triple("iPhone5,3", "iPhone", "N48AP"),
                Triple("iPhone5,4", "iPhone", "N49AP"),

                Triple("iPhone6,1", "iPhone", "N51AP"),
                Triple("iPhone6,2", "iPhone", "N53AP"),

                Triple("iPhone7,1", "iPhone", "N56AP"),
                Triple("iPhone7,2", "iPhone", "N61AP"),

                Triple("iPhone8,1", "iPhone", "N71AP")
        )

        val osVersions = arrayOf("8.1.1", "8.1.2", "8.1.3", "8.2", "8.3", "8.4", "8.4.1",
                "9.0", "9.0.1", "9.0.2", "9.1", "9.2", "9.2.1", "9.3", "9.3.1", "9.3.2", "9.3.3", "9.3.4")

        fun getDeviceInfo(hash: Long): SignatureOuterClass.Signature.DeviceInfo.Builder {
            // try to create unique identifier
            val random = Random(hash)
            val deviceInfo = SignatureOuterClass.Signature.DeviceInfo.newBuilder()

            val deviceId = ByteArray(16)
            random.nextBytes(deviceId)

            deviceInfo.setDeviceId(deviceId.toHexString())
            deviceInfo.setDeviceBrand("Apple")

            val device = devices[random.nextInt(devices.size)]
            deviceInfo.deviceModel = device.second
            deviceInfo.deviceModelBoot = "${device.first}${0.toChar()}"
            deviceInfo.hardwareManufacturer = "Apple"
            deviceInfo.hardwareModel = "${device.third}${0.toChar()}"
            deviceInfo.firmwareBrand = "iPhone OS"
            deviceInfo.firmwareType = osVersions[random.nextInt(osVersions.size)]
            return deviceInfo
        }
    }
}