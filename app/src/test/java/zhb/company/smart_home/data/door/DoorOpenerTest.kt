package zhb.company.smart_home.data.door

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import zhb.company.smart_home.data.local.SecretCipher
import zhb.company.smart_home.data.local.SessionStore
import zhb.company.smart_home.data.mqtt.BrokerCache
import zhb.company.smart_home.data.mqtt.FakeMqttConnector
import zhb.company.smart_home.data.mqtt.MqttConnection
import zhb.company.smart_home.data.mqtt.MqttConnectionListener
import zhb.company.smart_home.data.mqtt.MqttConnector
import zhb.company.smart_home.data.mqtt.MqttEndpoint
import zhb.company.smart_home.data.mqtt.MqttQualityOfService
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.data.node.CommandOutcome
import zhb.company.smart_home.data.remote.AuthApi
import zhb.company.smart_home.data.remote.AuthGrant
import zhb.company.smart_home.data.remote.MqttConfigSource
import zhb.company.smart_home.data.remote.dto.MqttClientConfigDto
import zhb.company.smart_home.data.repository.AuthRepository

@OptIn(ExperimentalCoroutinesApi::class)
class DoorOpenerTest {

    private class Store(var sealed: String? = "enc:refresh") : SessionStore {
        override suspend fun savedEmail() = "a@b.rs"
        override suspend fun sealedRefreshToken() = sealed
        override suspend fun legacyPassword(): String? = null
        override suspend fun saveSession(email: String, sealedRefreshToken: String) { sealed = sealedRefreshToken }
        override suspend fun clearSession() { sealed = null }
    }

    private class Cipher : SecretCipher {
        override fun encrypt(plain: String) = "enc:$plain"
        override fun decrypt(sealed: String) = sealed.removePrefix("enc:")
    }

    private class Api : AuthApi {
        override suspend fun signInWithPassword(email: String, password: String) = Result.success(AuthGrant("id", "refresh", 3600))
        override suspend fun refresh(refreshToken: String) = Result.success(AuthGrant("id", "refresh", 3600))
    }

    private class Brokers(var saved: List<MqttEndpoint>? = listOf(MqttEndpoint("broker", 1883, "u", "p"))) : BrokerCache {
        override suspend fun load() = saved
        override suspend fun save(endpoints: List<MqttEndpoint>) { saved = endpoints }
        override suspend fun clear() { saved = null }
    }

    /** A broker on which Node 3 answers the open command (or stays silent when [answer] is false). */
    private class DoorBroker(private val answer: Boolean = true) : MqttConnector {
        val subscriptions = mutableListOf<String>()
        val connections = mutableListOf<Conn>()

        inner class Conn(private val listener: MqttConnectionListener) : MqttConnection {
            val published = mutableListOf<Pair<String, String>>()
            var disconnected = false
            override fun publish(topic: String, payload: String, retain: Boolean, qos: MqttQualityOfService): Boolean {
                published += topic to payload
                val id = Regex(""""id":"([^"]+)"""").find(payload)!!.groupValues[1]
                if (answer) listener.onMessage("bozaSub/kuca/node3/result", """{"cmd":"door/open","id":"$id","ok":true,"rev":1}""", false)
                return true
            }
            override fun disconnect() { disconnected = true }
        }

        override fun connect(endpoint: MqttEndpoint, topicFilter: String, listener: MqttConnectionListener): MqttConnection {
            subscriptions += topicFilter
            return Conn(listener).also { connections += it }
        }
    }

    private val config = MqttClientConfigDto(mqttServer = "broker", mqttPort = "1883")

    private fun TestScopeHolder.opener(
        broker: MqttConnector,
        store: Store = Store(),
        brokers: Brokers = Brokers(),
        repo: SmartHomeRepository = SmartHomeRepository(MqttConfigSource { Result.success(config) }, FakeMqttConnector(), dispatcher),
    ) = DoorOpener(
        mqtt = repo,
        node3 = Node3Repository(repo, scope, commandTimeoutMs = 1_000, newId = { "app" }),
        auth = AuthRepository(Api(), store, Cipher()),
        brokers = brokers,
        configSource = MqttConfigSource { Result.success(config) },
        connector = broker,
        ioDispatcher = dispatcher,
        answerTimeoutMs = 1_000,
        newId = { "w1" },
    )

    private class TestScopeHolder(val scope: kotlinx.coroutines.CoroutineScope, val dispatcher: kotlinx.coroutines.CoroutineDispatcher)

    @Test
    fun `without the app connected, one short connection listens only for the answer`() = runTest {
        val broker = DoorBroker()
        val outcome = TestScopeHolder(backgroundScope, UnconfinedTestDispatcher(testScheduler)).opener(broker).open()

        assertTrue(outcome is CommandOutcome.Accepted)
        assertEquals(listOf("bozaSub/kuca/node3/result"), broker.subscriptions)
        val conn = broker.connections.single()
        assertEquals(listOf("bozaSub/kuca/node3/door/open" to """{"id":"w1"}"""), conn.published)
        assertTrue("closed right after the answer", conn.disconnected)
    }

    @Test
    fun `no answer from the node is a timeout, and the connection is closed`() = runTest {
        val broker = DoorBroker(answer = false)
        val outcome = TestScopeHolder(backgroundScope, UnconfinedTestDispatcher(testScheduler)).opener(broker).open()

        assertEquals(CommandOutcome.Timeout, outcome)
        assertTrue(broker.connections.single().disconnected)
    }

    @Test
    fun `signed out, the button does nothing`() = runTest {
        val broker = DoorBroker()
        val outcome = TestScopeHolder(backgroundScope, UnconfinedTestDispatcher(testScheduler)).opener(broker, store = Store(sealed = null)).open()

        assertEquals(CommandOutcome.NotConnected, outcome)
        assertTrue(broker.connections.isEmpty())
    }

    @Test
    fun `without saved brokers the config is read once and kept`() = runTest {
        val broker = DoorBroker()
        val brokers = Brokers(saved = null)
        val outcome = TestScopeHolder(backgroundScope, UnconfinedTestDispatcher(testScheduler)).opener(broker, brokers = brokers).open()

        assertTrue(outcome is CommandOutcome.Accepted)
        assertEquals(listOf(MqttEndpoint("broker", 1883)), brokers.saved)
    }

    @Test
    fun `with the app connected, its connection is used`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val appConnector = FakeMqttConnector()
        val repo = SmartHomeRepository(MqttConfigSource { Result.success(config) }, appConnector, dispatcher)
        repo.connect("token")
        val broker = DoorBroker()
        val opener = TestScopeHolder(backgroundScope, dispatcher).opener(broker, repo = repo)

        val result = async { opener.open() }
        runCurrent()
        repo.handleMessage("bozaSub/kuca/node3/result", """{"cmd":"door/open","id":"app","ok":true}""", retained = false)

        assertTrue(result.await() is CommandOutcome.Accepted)
        assertTrue("no second connection", broker.connections.isEmpty())
        assertTrue(appConnector.lastConnection!!.published.any { it.topic == "bozaSub/kuca/node3/door/open" })
    }

    @Test
    fun `a second tap while the first is handled does nothing`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val gate = CompletableDeferred<Unit>()
        val slow = object : BrokerCache {
            override suspend fun load(): List<MqttEndpoint> { gate.await(); return listOf(MqttEndpoint("broker", 1883)) }
            override suspend fun save(endpoints: List<MqttEndpoint>) = Unit
            override suspend fun clear() = Unit
        }
        val repo = SmartHomeRepository(MqttConfigSource { Result.success(config) }, FakeMqttConnector(), dispatcher)
        val opener = DoorOpener(
            repo, Node3Repository(repo, backgroundScope), AuthRepository(Api(), Store(), Cipher()), slow,
            MqttConfigSource { Result.success(config) }, DoorBroker(), dispatcher, 1_000, { "w" },
        )

        val first = async { opener.open() }
        runCurrent()
        assertNull(opener.open())
        gate.complete(Unit)
        assertTrue(first.await() is CommandOutcome.Accepted)
    }
}
