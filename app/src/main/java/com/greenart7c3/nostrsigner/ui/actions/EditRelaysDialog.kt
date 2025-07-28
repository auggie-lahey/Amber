package com.greenart7c3.nostrsigner.ui.actions

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.checkNotInMainThread
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.TimeUtils.formatLongToCustomDateTimeWithSeconds
import com.greenart7c3.nostrsigner.okhttp.HttpClientManager
import com.greenart7c3.nostrsigner.okhttp.OkHttpWebSocket
import com.greenart7c3.nostrsigner.relays.AmberListenerSingleton
import com.greenart7c3.nostrsigner.relays.BunkerValidationRelayListener
import com.greenart7c3.nostrsigner.service.Nip11Retriever
import com.greenart7c3.nostrsigner.service.NotificationDataSource
import com.greenart7c3.nostrsigner.ui.AccountStateViewModel
import com.greenart7c3.nostrsigner.ui.CenterCircularProgressIndicator
import com.greenart7c3.nostrsigner.ui.RelayCard
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.NostrClient
import com.vitorpamplona.ammolite.relays.RelaySetupInfo
import com.vitorpamplona.ammolite.relays.RelaySetupInfoToConnect
import com.vitorpamplona.ammolite.relays.RelayStats
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.nip65RelayList.RelayUrlFormatter
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DefaultRelaysScreen(
    modifier: Modifier,
    accountStateViewModel: AccountStateViewModel,
    account: Account,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val textFieldRelay = remember {
        mutableStateOf(TextFieldValue(""))
    }
    val isLoading = remember {
        mutableStateOf(false)
    }
    val relays2 =
        remember {
            val localRelays = mutableStateListOf<RelaySetupInfo>()
            Amber.instance.settings.defaultRelays.forEach {
                localRelays.add(
                    it.copy(),
                )
            }
            localRelays
        }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize(),
    ) {
        if (isLoading.value) {
            CenterCircularProgressIndicator(
                Modifier,
                text = stringResource(R.string.testing_relay),
            )
        } else {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .fillMaxSize(),
            ) {
                Text(
                    text = stringResource(R.string.manage_the_relays_used_for_communicating_with_external_applications),
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth(),
                    value = textFieldRelay.value.text,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    onValueChange = {
                        textFieldRelay.value = TextFieldValue(it)
                    },
                    keyboardActions = KeyboardActions(
                        onDone = {
                            scope.launch(Dispatchers.IO) {
                                onAddRelay(
                                    textFieldRelay,
                                    isLoading,
                                    relays2,
                                    scope,
                                    accountStateViewModel,
                                    account,
                                    context,
                                    onDone = {
                                        Amber.instance.settings = Amber.instance.settings.copy(
                                            defaultRelays = relays2,
                                        )
                                        LocalPreferences.saveSettingsToEncryptedStorage(Amber.instance.settings)
                                        scope.launch(Dispatchers.IO) {
                                            @Suppress("KotlinConstantConditions")
                                            if (BuildConfig.FLAVOR != "offline") {
                                                Amber.instance.checkForNewRelays()
                                                NotificationDataSource.stop()
                                                delay(2000)
                                                NotificationDataSource.start()
                                                isLoading.value = false
                                            } else {
                                                isLoading.value = false
                                            }
                                        }
                                    },
                                )
                            }
                        },
                    ),
                    label = {
                        Text("Relay")
                    },
                )

                AmberButton(
                    modifier = Modifier
                        .fillMaxWidth(),
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            onAddRelay(
                                textFieldRelay,
                                isLoading,
                                relays2,
                                scope,
                                accountStateViewModel,
                                account,
                                context,
                                onDone = {
                                    isLoading.value = true
                                    Amber.instance.settings = Amber.instance.settings.copy(
                                        defaultRelays = relays2,
                                    )
                                    LocalPreferences.saveSettingsToEncryptedStorage(Amber.instance.settings)
                                    scope.launch(Dispatchers.IO) {
                                        @Suppress("KotlinConstantConditions")
                                        if (BuildConfig.FLAVOR != "offline") {
                                            Amber.instance.checkForNewRelays()
                                            NotificationDataSource.stop()
                                            delay(2000)
                                            NotificationDataSource.start()
                                            isLoading.value = false
                                        } else {
                                            isLoading.value = false
                                        }
                                    }
                                },
                            )
                        }
                    },
                    text = stringResource(R.string.add),
                )

                LazyColumn(
                    Modifier
                        .weight(1f),
                ) {
                    items(relays2.size) {
                        RelayCard(
                            relay = relays2[it].url,
                            onClick = {
                                isLoading.value = true
                                relays2.removeAt(it)
                                Amber.instance.settings = Amber.instance.settings.copy(
                                    defaultRelays = relays2,
                                )
                                LocalPreferences.saveSettingsToEncryptedStorage(Amber.instance.settings)
                                scope.launch(Dispatchers.IO) {
                                    @Suppress("KotlinConstantConditions")
                                    if (BuildConfig.FLAVOR != "offline") {
                                        Amber.instance.checkForNewRelays()
                                        NotificationDataSource.stop()
                                        delay(2000)
                                        NotificationDataSource.start()
                                        isLoading.value = false
                                    } else {
                                        isLoading.value = false
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

fun onAddRelay(
    textFieldRelay: MutableState<TextFieldValue>,
    isLoading: MutableState<Boolean>,
    relays2: SnapshotStateList<RelaySetupInfo>,
    scope: CoroutineScope,
    accountStateViewModel: AccountStateViewModel,
    account: Account,
    context: Context,
    shouldCheckForBunker: Boolean = true,
    onDone: () -> Unit,
) {
    checkNotInMainThread()
    val url = textFieldRelay.value.text
    if (url.isNotBlank() && url != "/") {
        isLoading.value = true
        val isPrivateIp = Amber.instance.isPrivateIp(url)
        var addedWSS =
            if (!url.startsWith("wss://") && !url.startsWith("ws://")) {
                // TODO: How to identify relays on the local network?
                if (url.endsWith(".onion") || url.endsWith(".onion/") || isPrivateIp) {
                    "ws://$url"
                } else {
                    "wss://$url"
                }
            } else {
                url
            }
        if (url.endsWith("/")) addedWSS = addedWSS.dropLast(1)
        if (relays2.any { it.url == addedWSS }) {
            textFieldRelay.value = TextFieldValue("")
            isLoading.value = false
            return
        }
        val httpsUrl = RelayUrlFormatter.getHttpsUrl(addedWSS)
        val retriever = Nip11Retriever()
        scope.launch(Dispatchers.IO) {
            retriever.loadRelayInfo(
                httpsUrl,
                addedWSS,
                forceProxy = if (isPrivateIp) false else Amber.instance.settings.useProxy,
                onInfo = { info ->
                    scope.launch(Dispatchers.IO) secondLaunch@{
                        if (shouldCheckForBunker) {
                            val relays = Amber.instance.getSavedRelays()
                            if (relays.any { it.url == addedWSS }) {
                                relays2.add(
                                    RelaySetupInfo(
                                        addedWSS,
                                        read = true,
                                        write = true,
                                        feedTypes = COMMON_FEED_TYPES,
                                    ),
                                )
                                onDone()
                                textFieldRelay.value = TextFieldValue("")
                                isLoading.value = false
                                return@secondLaunch
                            }

                            val signer = NostrSignerInternal(KeyPair())
                            val encryptedContent = signer.signerSync.nip04Encrypt(
                                "Test bunker event",
                                signer.keyPair.pubKey.toHexKey(),
                            )
                            encryptedContent?.let {
                                val event = signer.signerSync.sign<Event>(
                                    TimeUtils.now(),
                                    NostrConnectEvent.KIND,
                                    arrayOf(arrayOf("p", signer.keyPair.pubKey.toHexKey())),
                                    it,
                                )

                                val isPrivateIp = Amber.instance.isPrivateIp(addedWSS)
                                val factory = OkHttpWebSocket.BuilderFactory { _, useProxy ->
                                    HttpClientManager.getHttpClient(useProxy)
                                }

                                event?.let { signedEvent ->
                                    AmberListenerSingleton.setListener(context)
                                    val socket = factory
                                    val client = NostrClient(
                                        socket,
                                    )
                                    val clientSub = NostrClient(
                                        socket,
                                    )
                                    AmberListenerSingleton.getListener()?.let { listener ->
                                        client.subscribe(listener)
                                    }
                                    client.reconnect(
                                        arrayOf(
                                            RelaySetupInfoToConnect(
                                                addedWSS,
                                                read = true,
                                                write = true,
                                                feedTypes = COMMON_FEED_TYPES,
                                                forceProxy = if (isPrivateIp) false else Amber.instance.settings.useProxy,
                                            ),
                                        ),
                                    )
                                    clientSub.reconnect(
                                        arrayOf(
                                            RelaySetupInfoToConnect(
                                                addedWSS,
                                                read = true,
                                                write = true,
                                                feedTypes = COMMON_FEED_TYPES,
                                                forceProxy = if (isPrivateIp) false else Amber.instance.settings.useProxy,
                                            ),
                                        ),
                                    )
                                    var filterResult = false
                                    val listener2 = BunkerValidationRelayListener(
                                        account,
                                        onReceiveEvent = { _, _, event ->
                                            if (event.kind == NostrConnectEvent.KIND && event.id == signedEvent.id) {
                                                filterResult = true
                                            }
                                        },
                                    )
                                    val relay = client.getRelay(addedWSS) ?: return@secondLaunch
                                    val relaySub = clientSub.getRelay(addedWSS) ?: return@secondLaunch
                                    client.subscribe(
                                        listener2,
                                    )
                                    clientSub.subscribe(
                                        listener2,
                                    )
                                    relay.connect()
                                    relaySub.connect()
                                    delay(3000)
                                    relay.sendFilter(
                                        UUID.randomUUID().toString().substring(0, 4),
                                        filters = listOf(
                                            TypedFilter(
                                                types = COMMON_FEED_TYPES,
                                                filter = SincePerRelayFilter(
                                                    kinds = listOf(NostrConnectEvent.KIND),
                                                    tags = mapOf("p" to listOf(signedEvent.pubKey)),
                                                ),
                                            ),
                                        ),
                                    )
                                    relaySub.sendFilter(
                                        UUID.randomUUID().toString().substring(0, 4),
                                        filters = listOf(
                                            TypedFilter(
                                                types = COMMON_FEED_TYPES,
                                                filter = SincePerRelayFilter(
                                                    kinds = listOf(NostrConnectEvent.KIND),
                                                    tags = mapOf("p" to listOf(signedEvent.pubKey)),
                                                ),
                                            ),
                                        ),
                                    )
                                    delay(3000)
                                    val result = client.sendAndWaitForResponse(
                                        signedEvent = signedEvent,
                                        relayList = listOf(RelaySetupInfo(addedWSS, read = true, write = true, setOf())),
                                    )
                                    if (result) {
                                        var count = 0
                                        while (!filterResult && count < 10) {
                                            delay(1000)
                                            count++
                                        }
                                    } else {
                                        AmberListenerSingleton.showErrorMessage()
                                        filterResult = true
                                    }

                                    AmberListenerSingleton.getListener()?.let { listener ->
                                        client.unsubscribe(listener)
                                    }
                                    client.unsubscribe(listener2)
                                    clientSub.unsubscribe(listener2)
                                    client.getRelay(addedWSS)?.disconnect()
                                    clientSub.getRelay(addedWSS)?.disconnect()

                                    if (result && filterResult) {
                                        relays2.add(
                                            RelaySetupInfo(
                                                addedWSS,
                                                read = true,
                                                write = true,
                                                feedTypes = COMMON_FEED_TYPES,
                                            ),
                                        )
                                        onDone()
                                    } else if (!filterResult) {
                                        accountStateViewModel.toast(
                                            context.getString(R.string.relay),
                                            context.getString(R.string.relay_filter_failed),
                                            onAccept = {
                                                relays2.add(
                                                    RelaySetupInfo(
                                                        addedWSS,
                                                        read = true,
                                                        write = true,
                                                        feedTypes = COMMON_FEED_TYPES,
                                                    ),
                                                )
                                                onDone()
                                            },
                                            onReject = {},
                                        )
                                    }

                                    textFieldRelay.value = TextFieldValue("")
                                }
                                isLoading.value = false
                            }
                        } else {
                            relays2.add(
                                RelaySetupInfo(
                                    addedWSS,
                                    read = true,
                                    write = true,
                                    feedTypes = COMMON_FEED_TYPES,
                                ),
                            )
                            onDone()
                            textFieldRelay.value = TextFieldValue("")
                        }
                    }
                },
                onError = { dirtyUrl, errorCode, exceptionMessage ->
                    isLoading.value = false
                    val msg =
                        when (errorCode) {
                            Nip11Retriever.ErrorCode.FAIL_TO_ASSEMBLE_URL ->
                                context.getString(
                                    R.string.relay_information_document_error_assemble_url,
                                    dirtyUrl,
                                    exceptionMessage,
                                )

                            Nip11Retriever.ErrorCode.FAIL_TO_REACH_SERVER ->
                                context.getString(
                                    R.string.relay_information_document_error_assemble_url,
                                    dirtyUrl,
                                    exceptionMessage,
                                )

                            Nip11Retriever.ErrorCode.FAIL_TO_PARSE_RESULT ->
                                context.getString(
                                    R.string.relay_information_document_error_assemble_url,
                                    dirtyUrl,
                                    exceptionMessage,
                                )

                            Nip11Retriever.ErrorCode.FAIL_WITH_HTTP_STATUS ->
                                context.getString(
                                    R.string.relay_information_document_error_assemble_url,
                                    dirtyUrl,
                                    exceptionMessage,
                                )
                        }

                    if (exceptionMessage?.contains("EACCES (Permission denied)") == true) {
                        accountStateViewModel.toast(
                            context.getString(R.string.unable_to_download_relay_document),
                            context.getString(R.string.network_permission_message),
                        )
                    } else if (exceptionMessage == "socket failed: EPERM (Operation not permitted)") {
                        accountStateViewModel.toast(
                            context.getString(R.string.unable_to_download_relay_document),
                            context.getString(R.string.network_permission_message),
                        )
                    } else {
                        accountStateViewModel.toast(
                            context.getString(R.string.unable_to_download_relay_document),
                            msg,
                        )
                    }
                    textFieldRelay.value = TextFieldValue("")
                },
            )
        }
    }
}

@Composable
fun RelayLogScreen(
    paddingValues: PaddingValues,
    url: String,
) {
    val context = LocalContext.current

    val flows = LocalPreferences.allSavedAccounts(context).map {
        Amber.instance.getDatabase(it.npub).applicationDao().getLogsByUrl(url)
    }.merge()

    val logs = flows.collectAsStateWithLifecycle(initialValue = emptyList())

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = paddingValues,
    ) {
        itemsIndexed(logs.value) { _, log ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        modifier = Modifier.padding(top = 16.dp),
                        text = formatLongToCustomDateTimeWithSeconds(log.time),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        modifier = Modifier.padding(top = 4.dp),
                        text = log.type,
                        fontSize = 20.sp,
                    )
                    Text(
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                        text = log.message,
                        fontSize = 20.sp,
                    )

                    Spacer(Modifier.weight(1f))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveRelaysScreen(
    modifier: Modifier,
    navController: NavController,
) {
    val relays2 =
        remember {
            mutableStateListOf<RelaySetupInfo>()
        }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            relays2.addAll(Amber.instance.getSavedRelays())
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize(),
    ) {
        relays2.forEach { relay ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 4.dp)
                    .clickable {
                        navController.navigate(
                            "RelayLogScreen/${
                                Base64
                                    .getEncoder()
                                    .encodeToString(relay.url.toByteArray())
                            }",
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        modifier = Modifier.padding(top = 16.dp),
                        text = relay.url,
                        fontSize = 24.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (Amber.instance.client.getRelay(relay.url)?.isConnected() == true) Color.Unspecified else Color.Gray,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                            text = if (Amber.instance.client.getRelay(relay.url)?.isConnected() == true) "${RelayStats.get(relay.url).pingInMs}ms ping" else "Unavailable",
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (Amber.instance.client.getRelay(relay.url)?.isConnected() == true) Color.Unspecified else Color.Gray,
                        )
                    }

                    Spacer(Modifier.weight(1f))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
