package com.brentvatne.media

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.XmlResourceParser
import android.os.Build
import androidx.annotation.RequiresApi
import com.brentvatne.react.R
import java.security.MessageDigest

class PackageValidator(context: Context) {
    private val permittedCallerXmlResId = R.xml.media_service_permitted_caller
    private val permittedCallerMap: Map<String, PermittedCallerInfo>
    private val packageManager = context.packageManager

    init {
        val parser = context.resources.getXml(permittedCallerXmlResId)
        permittedCallerMap = buildPermittedCallersList(parser)
    }

    fun isValidCaller(clientPackageName: String, clientUid: Int): Boolean {
        return when {
            clientUid == android.os.Process.myUid() -> true
            clientUid == android.os.Process.SYSTEM_UID -> true
            isPermittedCaller(clientPackageName, clientUid) -> true
            else -> false
        }
    }

    private fun isPermittedCaller(clientPackageName: String, clientUid: Int): Boolean {
        val packageInfo = getPackageInfo(clientPackageName) ?: return false
        val callerUid = packageInfo.applicationInfo.uid
        val callerSignature =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) getSignatureAboveV28(packageInfo)
            else getSignatureBelowV28(packageInfo)

        return clientUid == callerUid && permittedCallerMap[clientPackageName]?.signatures?.first {
            it.signature == callerSignature
        } != null
    }


    @RequiresApi(Build.VERSION_CODES.P)
    private fun getSignatureAboveV28(packageInfo: PackageInfo): String? =
        if (packageInfo.signingInfo.hasMultipleSigners() || packageInfo.signingInfo == null) {
            null
        } else {
            getSignatureSha256(packageInfo.signingInfo.signingCertificateHistory[0].toByteArray())
        }

    @Suppress("DEPRECATION")
    private fun getSignatureBelowV28(packageInfo: PackageInfo): String? =
        if (packageInfo.signatures == null || packageInfo.signatures.size != 1) {
            null
        } else {
            getSignatureSha256(packageInfo.signatures[0].toByteArray())
        }


    private fun getSignatureSha256(certificate: ByteArray): String {
        val md = MessageDigest.getInstance("SHA256")
        md.update(certificate)
        return md.digest().joinToString(":") { String.format("%02x", it) }
    }


    @Suppress("DEPRECATION")
    @SuppressLint("PackageManagerGetSignatures")
    private fun getPackageInfo(packageName: String) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_PERMISSIONS
            )
        } else {
            packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNATURES or PackageManager.GET_PERMISSIONS
            )
        }


    private fun buildPermittedCallersList(parse: XmlResourceParser): Map<String, PermittedCallerInfo> {
        val permittedMap = LinkedHashMap<String, PermittedCallerInfo>()
        var eventType = parse.next()
        while (eventType != XmlResourceParser.END_DOCUMENT) {
            if (eventType == XmlResourceParser.START_TAG) {
                parseTag(parse).let { callerInfo ->
                    val packageName = callerInfo.packageName
                    val callerInfoInMap = permittedMap[packageName]
                    if (callerInfoInMap != null) callerInfoInMap.signatures += callerInfo.signatures
                    else permittedMap[packageName] = callerInfo
                }
            }
            eventType = parse.next()
        }
        return permittedMap
    }

    private fun parseTag(parse: XmlResourceParser): PermittedCallerInfo {
        parse.next()
        val name = parse.getAttributeValue(null, "name")
        val packageName = parse.getAttributeValue(null, "package")
        val signatureInfoSet = mutableSetOf<SignatureInfo>()
        var eventType = parse.next()
        while (eventType != XmlResourceParser.END_TAG) {
            val isRelease = parse.getAttributeBooleanValue(null, "release", false)
            val signature = parse.nextText().replace("\\s|\\n".toRegex(), "").lowercase()
            signatureInfoSet += SignatureInfo(isRelease, signature)
            eventType = parse.next()
        }

        return PermittedCallerInfo(name, packageName, signatureInfoSet)
    }
}

data class PermittedCallerInfo(
    val name: String,
    val packageName: String,
    val signatures: MutableSet<SignatureInfo>
)

data class SignatureInfo(val isRelease: Boolean, val signature: String)
