package one.ruri.authmeplus

import org.bukkit.entity.Player
import java.lang.reflect.Method
import java.util.logging.Logger

class Bridge(
    private val logger: Logger,
) {
    private var authMeApiInstance: Any? = null
    private var mIsAuthenticated: Method? = null
    private var mForceLogin: Method? = null
    private var mIsRegistered: Method? = null
    private var mRegisterPlayer: Method? = null

    fun findAuthMeApi(): Boolean {
        try {
            val c = Class.forName("fr.xephi.authme.api.v3.AuthMeApi")
            val getInstance = c.getMethod("getInstance")
            val instance = getInstance.invoke(null)
            val isAuth = c.getMethod("isAuthenticated", Player::class.java)
            val force = c.getMethod("forceLogin", Player::class.java)
            authMeApiInstance = instance
            mIsAuthenticated = isAuth
            mForceLogin = force
            logger.info("Found AuthMe API: fr.xephi.authme.api.v3.AuthMeApi")
            tryFindOptionalMethods(c, false)
            return true
        } catch (_: Throwable) {
            try {
                val c = Class.forName("fr.xephi.authme.api.API")
                val getInstance = c.getMethod("getInstance")
                val instance = getInstance.invoke(null)
                val isAuth = c.getMethod("isAuthenticated", Player::class.java)
                val force = c.getMethod("forceLogin", Player::class.java)
                authMeApiInstance = instance
                mIsAuthenticated = isAuth
                mForceLogin = force
                logger.info("Found AuthMe API: fr.xephi.authme.api.API")
                tryFindOptionalMethods(c, false)
                return true
            } catch (_: Throwable) {
                try {
                    val main = Class.forName("fr.xephi.authme.AuthMe")
                    val getInstance = main.getMethod("getInstance")
                    val mainInstance = getInstance.invoke(null)
                    val getAPI = main.getMethod("getAPI")
                    val apiInstance = getAPI.invoke(mainInstance)
                    val apiClass = apiInstance!!.javaClass
                    val isAuth = apiClass.getMethod("isAuthenticated", Player::class.java)
                    val force = apiClass.getMethod("forceLogin", Player::class.java)
                    authMeApiInstance = apiInstance
                    mIsAuthenticated = isAuth
                    mForceLogin = force
                    logger.info("Found AuthMe API via fr.xephi.authme.AuthMe.getInstance().getAPI()")
                    tryFindOptionalMethods(apiClass, true)
                    return true
                } catch (_: Throwable) {
                    return false
                }
            }
        }
    }

    private fun tryFindOptionalMethods(
        c: Class<*>,
        isOldFallback: Boolean,
    ) {
        try {
            mIsRegistered = c.getMethod("isRegistered", String::class.java)
        } catch (_: NoSuchMethodException) {
            logger.info("AuthMe API does not expose isRegistered")
        }
        try {
            mRegisterPlayer = c.getMethod("registerPlayer", String::class.java, String::class.java)
        } catch (_: NoSuchMethodException) {
            logger.info("AuthMe API does not expose registerPlayer")
        }
    }

    fun isAuthenticated(p: Player): Boolean {
        val method = mIsAuthenticated ?: return false
        return try {
            val res = method.invoke(authMeApiInstance, p)
            (res as? Boolean) ?: false
        } catch (e: Exception) {
            logger.warning("Error invoking isAuthenticated: ${e.message}")
            false
        }
    }

    fun forceLogin(p: Player) {
        val method = mForceLogin ?: return
        try {
            method.invoke(authMeApiInstance, p)
        } catch (e: Exception) {
            logger.warning("Error invoking forceLogin: ${e.message}")
        }
    }

    fun isRegistered(name: String): Boolean {
        val method = mIsRegistered ?: return false
        return try {
            val res = method.invoke(authMeApiInstance, name.lowercase())
            (res as? Boolean) ?: false
        } catch (e: Exception) {
            logger.warning("Error invoking isRegistered: ${e.message}")
            false
        }
    }

    fun registerPlayer(
        p: Player,
        password: String,
    ): Boolean {
        val method = mRegisterPlayer
        if (method == null) {
            logger.warning("Cannot register player: registerPlayer API not available")
            return false
        }
        return try {
            val res = method.invoke(authMeApiInstance, p.name, password)
            if (res is Boolean && !res) {
                logger.warning("registerPlayer returned false for ${p.name}")
                return false
            }
            true
        } catch (e: Exception) {
            logger.warning("Error invoking registerPlayer: ${e.message}")
            false
        }
    }
}
