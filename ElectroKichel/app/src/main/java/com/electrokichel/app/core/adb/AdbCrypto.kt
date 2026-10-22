package com.electrokichel.app.core.adb

import android.content.Context
import android.util.Base64
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/** Par de claves RSA-2048 para ADB, persistido, y la codificación de la clave pública en el formato de adb. */
class AdbCrypto private constructor(val keyPair: KeyPair) {

    fun signPayload(token: ByteArray): ByteArray {
        // ADB firma el token con PKCS#1 v1.5 y el prefijo SHA-1 DigestInfo (el token ya es el hash de 20 bytes).
        val s = Signature.getInstance("NONEwithRSA")
        s.initSign(keyPair.private)
        s.update(SHA1_PREFIX + token)
        return s.sign()
    }

    /** Clave pública en el formato de adb: estructura RSAPublicKey (little endian) en Base64 + " nombre\u0000". */
    fun adbPublicKey(name: String = "electrokichel@android"): ByteArray {
        val pub = keyPair.public as RSAPublicKey
        val WORDS = 64
        val r32 = BigInteger.ONE.shiftLeft(32)
        val n = pub.modulus
        val n0inv = r32.subtract(n.mod(r32).modInverse(r32)).toLong()
        val r = BigInteger.ONE.shiftLeft(2048)
        val rr = r.multiply(r).mod(n)
        val buf = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(WORDS)
        buf.putInt((n0inv and 0xFFFFFFFFL).toInt())
        putBig(buf, n, WORDS)
        putBig(buf, rr, WORDS)
        buf.putInt(pub.publicExponent.toInt())
        val b64 = Base64.encodeToString(buf.array(), Base64.NO_WRAP)
        return (b64 + " " + name + "\u0000").toByteArray()
    }

    private fun putBig(buf: ByteBuffer, value: BigInteger, words: Int) {
        val mask = BigInteger.ONE.shiftLeft(32).subtract(BigInteger.ONE)
        var v = value
        for (i in 0 until words) { buf.putInt(v.and(mask).toInt()); v = v.shiftRight(32) }
    }

    companion object {
        private val SHA1_PREFIX = byteArrayOf(
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14
        )

        fun load(ctx: Context): AdbCrypto {
            val prefs = ctx.getSharedPreferences("adb_keypair", Context.MODE_PRIVATE)
            val priv = prefs.getString("priv", null); val pub = prefs.getString("pub", null)
            if (priv != null && pub != null) {
                val kf = KeyFactory.getInstance("RSA")
                val pr = kf.generatePrivate(PKCS8EncodedKeySpec(Base64.decode(priv, Base64.DEFAULT))) as RSAPrivateKey
                val pu = kf.generatePublic(X509EncodedKeySpec(Base64.decode(pub, Base64.DEFAULT))) as RSAPublicKey
                return AdbCrypto(KeyPair(pu, pr))
            }
            val gen = KeyPairGenerator.getInstance("RSA"); gen.initialize(2048)
            val kp = gen.generateKeyPair()
            prefs.edit()
                .putString("priv", Base64.encodeToString(kp.private.encoded, Base64.DEFAULT))
                .putString("pub", Base64.encodeToString(kp.public.encoded, Base64.DEFAULT))
                .apply()
            return AdbCrypto(kp)
        }
    }
}
