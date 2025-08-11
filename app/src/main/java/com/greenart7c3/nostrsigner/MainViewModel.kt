package com.greenart7c3.nostrsigner

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.service.BunkerRequestUtils
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.greenart7c3.nostrsigner.service.MultiEventScreenIntents.intents
import com.greenart7c3.nostrsigner.ui.AccountStateViewModel
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.utils.Hex
import kotlin.text.isNotBlank
import kotlin.text.startsWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@SuppressLint("StaticFieldLeak")
class MainViewModel(val context: Context) : ViewModel() {
    val intents = MutableStateFlow<List<IntentData>>(listOf())
    var navController: NavHostController? = null

    fun getAccount(userFromIntent: String?): String? {
        val currentAccount = LocalPreferences.currentAccount(context)
        try {
            if (!userFromIntent.isNullOrBlank()) {
                if (userFromIntent.startsWith("npub")) {
                    if (LocalPreferences.containsAccount(context, userFromIntent)) {
                        Log.d(Amber.TAG, "getAccount: $userFromIntent")
                        return userFromIntent
                    }
                } else {
                    val localNpub = Hex.decode(userFromIntent).toNpub()
                    if (LocalPreferences.containsAccount(context, localNpub)) {
                        Log.d(Amber.TAG, "getAccount: $localNpub")
                        return localNpub
                    }
                }
            }

            val pubKeys =
                intents.value.mapNotNull {
                    it.event?.pubKey
                }.filter { it.isNotBlank() } + BunkerRequestUtils.getBunkerRequests().mapNotNull {
                    val parsed = Nip19Parser.uriToRoute(it.currentAccount)?.entity
                    when (parsed) {
                        is NPub -> parsed.hex
                        else -> null
                    }
                }

            if (pubKeys.isEmpty()) {
                if (currentAccount != null && LocalPreferences.containsAccount(context, currentAccount)) {
                    Log.d(Amber.TAG, "getAccount: $currentAccount")
                    return currentAccount
                }

                val acc = LocalPreferences.allSavedAccounts(context).firstOrNull()
                if (acc != null) {
                    Log.d(Amber.TAG, "getAccount: ${acc.npub}")
                    return acc.npub
                } else {
                    Log.d(Amber.TAG, "getAccount: null")
                    return null
                }
            }

            val npub = Hex.decode(pubKeys.first()).toNpub()
            Log.d(Amber.TAG, "getAccount: $npub")
            return npub
        } catch (e: Exception) {
            Log.e(Amber.TAG, "Error getting account", e)
            if (currentAccount != null && LocalPreferences.containsAccount(context, currentAccount)) {
                Log.d(Amber.TAG, "getAccount: $currentAccount")
                return currentAccount
            }

            val acc = LocalPreferences.allSavedAccounts(context).firstOrNull()
            if (acc != null) {
                Log.d(Amber.TAG, "getAccount: ${acc.npub}")
                return acc.npub
            } else {
                Log.d(Amber.TAG, "getAccount: null")
                return null
            }
        }
    }

    fun showBunkerRequests(callingPackage: String?, accountStateViewModel: AccountStateViewModel? = null) {
        val requests =
            BunkerRequestUtils.getBunkerRequests().map {
                it.copy()
            }

        viewModelScope.launch(Dispatchers.IO) {
            val account = LocalPreferences.loadFromEncryptedStorage(context)
            account?.let { acc ->
                requests.forEach {
                    val contentIntent =
                        Intent(Amber.instance, MainActivity::class.java).apply {
                            data = "nostrsigner:".toUri()
                        }
                    contentIntent.putExtra("bunker", it.toJson())
                    contentIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    val intentData = IntentUtils.getIntentData(context, contentIntent, callingPackage, Route.IncomingRequest.route, acc)
                    contentIntent.putExtra("current_account", acc.npub)
                    if (intentData != null) {
                        if (intents.value.none { item -> item.id == intentData.id }) {
                            intents.value += listOf(intentData)
                        }
                    }
                }
            }

            if (requests.isNotEmpty()) {
                val npub = getAccount(null)
                val currentAccount = LocalPreferences.currentAccount(context)
                if (currentAccount != null && npub != null && currentAccount != npub && npub.isNotBlank()) {
                    if (npub.startsWith("npub")) {
                        Log.d(Amber.TAG, "Switching account to $npub")
                        if (LocalPreferences.containsAccount(context, npub)) {
                            accountStateViewModel?.switchUser(npub, Route.IncomingRequest.route)
                        }
                    } else {
                        val localNpub = Hex.decode(npub).toNpub()
                        Log.d(Amber.TAG, "Switching account to $localNpub")
                        if (LocalPreferences.containsAccount(context, localNpub)) {
                            accountStateViewModel?.switchUser(localNpub, Route.IncomingRequest.route)
                        }
                    }
                }
            }
        }
    }

    fun onNewIntent(
        intent: Intent,
        callingPackage: String?,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val account = LocalPreferences.loadFromEncryptedStorage(context)
            account?.let { acc ->
                val intentData = IntentUtils.getIntentData(context, intent, callingPackage, intent.getStringExtra("route"), acc)
                if (intentData != null) {
                    if (intents.value.none { item -> item.id == intentData.id }) {
                        intents.value += listOf(intentData)
                    }
                    intents.value =
                        intents.value.map {
                            it.copy()
                        }.toMutableList()

                    intent.getStringExtra("route")?.let {
                        viewModelScope.launch(Dispatchers.Main) {
                            var error = true
                            var count = 0
                            while (error && count < 10) {
                                delay(100)
                                count++
                                try {
                                    navController?.navigate(Route.IncomingRequest.route) {
                                        popUpTo(0)
                                    }
                                    error = false
                                } catch (e: Exception) {
                                    Log.e(Amber.TAG, "Error showing bunker requests", e)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
