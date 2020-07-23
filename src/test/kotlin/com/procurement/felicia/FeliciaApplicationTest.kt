package com.procurement.felicia

import com.procurement.felicia.application.network.model.Socket
import com.procurement.felicia.domain.splitBy
import com.procurement.felicia.infrastructure.web.controllers.kafka
import com.procurement.felicia.infrastructure.web.http.CLIENT_NOT_FOUND
import com.procurement.felicia.infrastructure.web.http.HEADER_GROUP_ID
import com.procurement.felicia.infrastructure.web.http.HEADER_HOSTS
import com.procurement.felicia.infrastructure.web.http.HEADER_TIMEOUT
import com.procurement.felicia.infrastructure.web.http.HEADER_TOPIC
import com.procurement.felicia.infrastructure.web.http.HEADER_TOPICS
import com.procurement.felicia.infrastructure.web.http.QUERY_PATTERN
import io.ktor.application.Application
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.cookiesSession
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.Container
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper
import java.time.Duration
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FeliciaApplicationTest {

    companion object {
        private val ENDPOINT_SUBSCRIBE = "/kafka/subscribe"
        private val ENDPOINT_FIND      = "/kafka/find"

        private val TIMEOUT_SECONDS    = "3"

        private val mapper             = ObjectMapper()
    }

    private lateinit var TOPIC_ONE: String
    private lateinit var TOPIC_TWO: String
    private lateinit var GROUP_ID : String

    private val SAMPLE_MESSAGE: (id: String) -> String = {
        """
                {
                    "id": ${it},
                    "isTest": true,
                    "name": "kafka-test"
                }
            """.trimIndent()
    }

    class MessageFactory {
        companion object {
            fun newMessage(): Message {
                return Message(
                    id = UUID.randomUUID().toString(),
                    name = "Kafka-test",
                    isTest = true
                )
            }
        }

        data class Message(
            val id: String,
            val isTest: Boolean,
            val name: String
        )
    }

    private fun MessageFactory.Message.toJson(): String = mapper.writeValueAsString(this)



    private val kafkaContainer = KafkaContainer().withEmbeddedZookeeper().apply { start() }
    private val kafkaProducer = createKafkaProducer(kafkaContainer.bootstrapServers)

    private val host = kafkaContainer.bootstrapServers  // PLAINTEXT://host:port
        .splitBy("//")                         // [PLAINTEXT:, host:port]
        .elementAt(1)                            // host:port
        .let { host -> Socket.tryCreate(host).get }


    /**
     * TODO app
     * result response handler
     * design error response format
     * list -> collection
     */

    @BeforeEach
    fun preparement() {
        TOPIC_ONE = UUID.randomUUID().toString()
        TOPIC_TWO = UUID.randomUUID().toString()
        GROUP_ID  = UUID.randomUUID().toString()
    }

    @Test
    @DisplayName("Error when subscribe without $HEADER_HOSTS specifying")
    fun `try subscribe without hosts`() = withTestApplication(Application::kafka) {

        cookiesSession {
            handleRequest(HttpMethod.Post, ENDPOINT_SUBSCRIBE) {
                addHeader(HEADER_TOPICS, TOPIC_ONE)
            }.apply {
                assertEquals(HttpStatusCode.BadRequest, response.status())
                assertNotNull(response.content)
                assertTrue(response.content!!.contains(HEADER_HOSTS))
            }
        }

    }

    @Test
    @DisplayName("Error when subscribe without $HEADER_TOPICS specifying")
    fun `try subscribe without topics`() = withTestApplication(Application::kafka) {

        cookiesSession {
            handleRequest(HttpMethod.Post, ENDPOINT_SUBSCRIBE) {
                addHeader(HEADER_HOSTS, host.toString())
            }.apply {
                assertEquals(HttpStatusCode.BadRequest, response.status())
                assertNotNull(response.content)
                assertTrue(response.content!!.contains(HEADER_TOPICS))
            }
        }

    }

    @Test
    @DisplayName("Error when '/find' without subscribe")
    fun `try find message without subscription`(): Unit = withTestApplication(Application::kafka) {

        handleRequest(HttpMethod.Post, ENDPOINT_FIND)
            .apply {
                assertEquals(HttpStatusCode.BadRequest, response.status())
                assertEquals(CLIENT_NOT_FOUND, response.content)
            }

    }


    @Test
    @DisplayName("Error when '/find' without specifying pattern parameter in headers")
    fun `try find message - without pattern`() = withTestApplication(Application::kafka) {

        cookiesSession {
            handleRequest(HttpMethod.Post, ENDPOINT_SUBSCRIBE) {
                addHeader(HEADER_HOSTS, host.toString())
                addHeader(HEADER_TOPICS, TOPIC_ONE)
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
            }

            kafkaProducer.send(ProducerRecord(TOPIC_ONE, MessageFactory.newMessage().toJson()))

            handleRequest(HttpMethod.Post, ENDPOINT_FIND) {
                addHeader(HEADER_TOPIC, TOPIC_ONE)
                addHeader(HEADER_TIMEOUT, TIMEOUT_SECONDS)
            }.apply {
                assertEquals(HttpStatusCode.BadRequest, response.status())
                assertNotNull(response.content)
                assertTrue(response.content!!.contains(QUERY_PATTERN))
            }
        }

    }

    @Test
    @DisplayName(value =
        "Subscribe topic (T)"        + " - " +
        "Send message    (M) -> (T)" + " - " +
        "Receive         (M) <- (T)"
    )
    fun `try find message - normal`() = withTestApplication(Application::kafka) {

        cookiesSession {
            handleRequest(HttpMethod.Post, ENDPOINT_SUBSCRIBE) {
                addHeader(HEADER_HOSTS, host.toString())
                addHeader(HEADER_TOPICS, TOPIC_ONE)
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
            }

            val expectedMessage = MessageFactory.newMessage()

            kafkaProducer.send(ProducerRecord(TOPIC_ONE, expectedMessage.toJson()))

            handleRequest(HttpMethod.Post, "$ENDPOINT_FIND?$QUERY_PATTERN=${expectedMessage.id}") {
                addHeader(HEADER_TOPIC, TOPIC_ONE)
                addHeader(HEADER_TIMEOUT, TIMEOUT_SECONDS)
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals(expectedMessage.toJson(), response.content)
            }
        }

    }

    @Test
    @DisplayName(value =
        "Subscribe topic (T)"             + " - " +
        "Send message    (M1, M2) -> (T)" + " - " +
        "Receive         (M1, M2) <- (T)"
    )
    fun `try find two message from one topic `() = withTestApplication(Application::kafka) {

        cookiesSession {
            handleRequest(HttpMethod.Post, ENDPOINT_SUBSCRIBE) {
                addHeader(HEADER_HOSTS, host.toString())
                addHeader(HEADER_TOPICS, TOPIC_ONE)
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
            }

            val expectedMessage1 = MessageFactory.newMessage()
            val expectedMessage2 = MessageFactory.newMessage()

            kafkaProducer.send(ProducerRecord(TOPIC_ONE, expectedMessage1.toJson()))
            kafkaProducer.send(ProducerRecord(TOPIC_ONE, expectedMessage2.toJson()))

            handleRequest(HttpMethod.Post, "$ENDPOINT_FIND?$QUERY_PATTERN=${expectedMessage1.id}") {
                addHeader(HEADER_TOPIC, TOPIC_ONE)
                addHeader(HEADER_TIMEOUT, TIMEOUT_SECONDS)
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals(expectedMessage1.toJson(), response.content)
            }
            handleRequest(HttpMethod.Post, "$ENDPOINT_FIND?$QUERY_PATTERN=${expectedMessage2.id}") {
                addHeader(HEADER_TOPIC, TOPIC_ONE)
                addHeader(HEADER_TIMEOUT, TIMEOUT_SECONDS)
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals(expectedMessage2.toJson(), response.content)
            }
        }

    }

    @Test
    @DisplayName(value =
        "Subscribe topic (T1, T2)"                           + " - " +
        "Send message    (M1 -> T1), (M2 -> T2), (M3 -> T1)" + " - " +
        "Receive         (M1 <- T1), (M2 <- T2), (M3 <- T1)"
    )
    fun `try find messages from different topics`() = withTestApplication(Application::kafka) {

        cookiesSession {
            handleRequest(HttpMethod.Post, ENDPOINT_SUBSCRIBE) {
                addHeader(HEADER_HOSTS, host.toString())
                addHeader(HEADER_TOPICS, listOf(TOPIC_ONE, TOPIC_TWO).joinToString(separator = ","))
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
            }

            val expectedMessage1 = MessageFactory.newMessage()
            val expectedMessage2 = MessageFactory.newMessage()
            val expectedMessage1_2 = MessageFactory.newMessage()

            kafkaProducer.send(ProducerRecord(TOPIC_ONE, expectedMessage1.toJson()))
            kafkaProducer.send(ProducerRecord(TOPIC_TWO, expectedMessage2.toJson()))
            kafkaProducer.send(ProducerRecord(TOPIC_ONE, expectedMessage1_2.toJson()))

            handleRequest(HttpMethod.Post, "$ENDPOINT_FIND?$QUERY_PATTERN=${expectedMessage1.id}") {
                addHeader(HEADER_TOPIC, TOPIC_ONE)
                addHeader(HEADER_TIMEOUT, TIMEOUT_SECONDS)
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals(expectedMessage1.toJson(), response.content)
            }

            handleRequest(HttpMethod.Post, "$ENDPOINT_FIND?$QUERY_PATTERN=${expectedMessage2.id}") {
                addHeader(HEADER_TOPIC, TOPIC_TWO)
                addHeader(HEADER_TIMEOUT, TIMEOUT_SECONDS)
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals(expectedMessage2.toJson(), response.content)
            }

            handleRequest(HttpMethod.Post, "$ENDPOINT_FIND?$QUERY_PATTERN=${expectedMessage1_2.id}") {
                addHeader(HEADER_TOPIC, TOPIC_ONE)
                addHeader(HEADER_TIMEOUT, TIMEOUT_SECONDS)
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals(expectedMessage1_2.toJson(), response.content)
            }
        }

    }

    @Test
    @DisplayName("Error when '/find' message that not exists")
    fun `try find unsended message`() = withTestApplication(Application::kafka) {

        val incorrectMessagePattern = "some_unexpected_pattern"

        cookiesSession {
            handleRequest(HttpMethod.Post, ENDPOINT_SUBSCRIBE) {
                addHeader(HEADER_HOSTS, host.toString())
                addHeader(HEADER_TOPICS, listOf(TOPIC_ONE, TOPIC_TWO).joinToString(separator = ","))
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
            }

            handleRequest(HttpMethod.Post, "$ENDPOINT_FIND?$QUERY_PATTERN=${incorrectMessagePattern}") {
                addHeader(HEADER_TOPIC, TOPIC_ONE)
                addHeader(HEADER_TIMEOUT, TIMEOUT_SECONDS)
            }.apply {
                assertEquals(HttpStatusCode.NoContent, response.status())
            }
        }

    }

    @Test
    @DisplayName("Offsets not shift when message not found")
    fun `offset not shifting when message not found`() = withTestApplication(Application::kafka) {

        val unexpectedPattern = "some-unexpenced-pattern"

        cookiesSession {

            val expectedMessage1 = MessageFactory.newMessage()
            val expectedMessage2 = MessageFactory.newMessage()
            val expectedMessage3 = MessageFactory.newMessage()

            kafkaProducer.send(ProducerRecord(TOPIC_ONE, expectedMessage1.toJson()))

            handleRequest(HttpMethod.Post, ENDPOINT_SUBSCRIBE) {
                addHeader(HEADER_HOSTS, host.toString())
                addHeader(HEADER_TOPICS, listOf(TOPIC_ONE, TOPIC_TWO).joinToString(separator = ","))
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
            }

            kafkaProducer.send(ProducerRecord(TOPIC_ONE, expectedMessage2.toJson()))
            kafkaProducer.send(ProducerRecord(TOPIC_ONE, expectedMessage3.toJson()))

            handleRequest(HttpMethod.Post, "$ENDPOINT_FIND?$QUERY_PATTERN=${unexpectedPattern}") {
                addHeader(HEADER_TOPIC, TOPIC_ONE)
                addHeader(HEADER_TIMEOUT, TIMEOUT_SECONDS)
            }.apply {
                assertEquals(HttpStatusCode.NoContent, response.status())
            }

            handleRequest(HttpMethod.Post, "$ENDPOINT_FIND?$QUERY_PATTERN=${expectedMessage2.id}") {
                addHeader(HEADER_TOPIC, TOPIC_ONE)
                addHeader(HEADER_TIMEOUT, TIMEOUT_SECONDS)
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals(expectedMessage2.toJson(), response.content)
            }
        }
    }

    @Test
    @DisplayName(value =
        "Subscribe topic (T1)"                   + " - " +
        "Send message    (M1 -> T1)"             + " - " +
        "Receive         (M1 <- T1)"             + " - " +
        "Send message    (M2 -> T1), (M3 -> T2)" + " - " +
        "Resubscribe     (T1, T2)"               + " - " +
        "Send message    (M4 -> T1), (M5 -> T2)" + " - " +
        "Assert          (M2 <! T1), (M3 <! T2)" + " - " +
        "Receive         (M4 <- T1), (M5 <- T2)"
    )
    fun `reassign and find`() = withTestApplication(Application::kafka) {
        val expectedMessageA1 = MessageFactory.newMessage()
        val expectedMessageA2 = MessageFactory.newMessage()

        val preResubscribeMessageA = MessageFactory.newMessage()
        val preResubscribeMessageB = MessageFactory.newMessage()

        val expectedMessageB1 = MessageFactory.newMessage()


        cookiesSession {
            handleRequest(HttpMethod.Post, ENDPOINT_SUBSCRIBE) {
                addHeader(HEADER_HOSTS, host.toString())
                addHeader(HEADER_TOPICS, listOf(TOPIC_ONE).joinToString(separator = ","))
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
            }

            kafkaProducer.send(ProducerRecord(TOPIC_ONE, expectedMessageA1.toJson()))

            handleRequest(HttpMethod.Post, "$ENDPOINT_FIND?$QUERY_PATTERN=${expectedMessageA1.id}") {
                addHeader(HEADER_TOPIC, TOPIC_ONE)
                addHeader(HEADER_TIMEOUT, TIMEOUT_SECONDS)
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals(expectedMessageA1.toJson(), response.content)
            }

            // Some another messages
            kafkaProducer.send(ProducerRecord(TOPIC_ONE, preResubscribeMessageA.toJson()))
            kafkaProducer.send(ProducerRecord(TOPIC_TWO, preResubscribeMessageB.toJson()))

            handleRequest(HttpMethod.Post, ENDPOINT_SUBSCRIBE) {
                addHeader(HEADER_HOSTS, host.toString())
                addHeader(HEADER_TOPICS, listOf(TOPIC_ONE, TOPIC_TWO).joinToString(separator = ","))
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
            }

            kafkaProducer.send(ProducerRecord(TOPIC_ONE, expectedMessageA2.toJson()))
            kafkaProducer.send(ProducerRecord(TOPIC_TWO, expectedMessageB1.toJson()))

            handleRequest(HttpMethod.Post, "$ENDPOINT_FIND?$QUERY_PATTERN=${preResubscribeMessageA.id}") {
                addHeader(HEADER_TOPIC, TOPIC_ONE)
                addHeader(HEADER_TIMEOUT, TIMEOUT_SECONDS)
            }.apply {
                assertEquals(HttpStatusCode.NoContent, response.status())
            }

            handleRequest(HttpMethod.Post, "$ENDPOINT_FIND?$QUERY_PATTERN=${preResubscribeMessageB.id}") {
                addHeader(HEADER_TOPIC, TOPIC_TWO)
                addHeader(HEADER_TIMEOUT, TIMEOUT_SECONDS)
            }.apply {
                assertEquals(HttpStatusCode.NoContent, response.status())
            }

            handleRequest(HttpMethod.Post, "$ENDPOINT_FIND?$QUERY_PATTERN=${expectedMessageA2.id}") {
                addHeader(HEADER_TOPIC, TOPIC_ONE)
                addHeader(HEADER_TIMEOUT, TIMEOUT_SECONDS)
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals(expectedMessageA2.toJson(), response.content)
            }

            handleRequest(HttpMethod.Post, "$ENDPOINT_FIND?$QUERY_PATTERN=${expectedMessageB1.id}") {
                addHeader(HEADER_TOPIC, TOPIC_TWO)
                addHeader(HEADER_TIMEOUT, TIMEOUT_SECONDS)
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals(expectedMessageB1.toJson(), response.content)
            }
        }
    }

    @Test
    @DisplayName("Any message consumed if pattern is empty string")
    fun `pattern - empty string`() = withTestApplication(Application::kafka) {

        cookiesSession {

            val expectedMessage1 = MessageFactory.newMessage()
            val expectedMessage2 = MessageFactory.newMessage()
            val expectedMessage3 = MessageFactory.newMessage()

            kafkaProducer.send(ProducerRecord(TOPIC_ONE, expectedMessage1.toJson()))

            handleRequest(HttpMethod.Post, ENDPOINT_SUBSCRIBE) {
                addHeader(HEADER_HOSTS, host.toString())
                addHeader(HEADER_TOPICS, listOf(TOPIC_ONE).joinToString(separator = ","))
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
            }

            kafkaProducer.send(ProducerRecord(TOPIC_ONE, expectedMessage2.toJson()))
            kafkaProducer.send(ProducerRecord(TOPIC_ONE, expectedMessage3.toJson()))

            handleRequest(HttpMethod.Post, "$ENDPOINT_FIND?$QUERY_PATTERN=") {
                addHeader(HEADER_TOPIC, TOPIC_ONE)
                addHeader(HEADER_TIMEOUT, TIMEOUT_SECONDS)
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals(expectedMessage2.toJson(), response.content)
            }
        }
    }

    @Test
    @DisplayName("Error when subscribe with group id that already connected via CLI")
    fun `client already connested with CLI`() = withTestApplication(Application::kafka) {

        cookiesSession {

            val expectedMessage1 = MessageFactory.newMessage()

            launch {
                kafkaContainer.exec("kafka-console-consumer --bootstrap-server localhost:9092 --group $GROUP_ID --topic $TOPIC_ONE --from-beginning ")
            }

            runBlocking {
                delay(Duration.ofSeconds(2).toMillis())
            }

            kafkaProducer.send(ProducerRecord(TOPIC_ONE, expectedMessage1.toJson()))

            handleRequest(HttpMethod.Post, ENDPOINT_SUBSCRIBE) {
                addHeader(HEADER_HOSTS, host.toString())
                addHeader(HEADER_GROUP_ID, GROUP_ID)
                addHeader(HEADER_TOPICS, listOf(TOPIC_ONE).joinToString(separator = ","))
            }.apply {
                assertEquals(HttpStatusCode.Conflict, response.status())
            }

        }
    }

}

fun createKafkaProducer(host: String): KafkaProducer<String, String> {
    val props = Properties().apply {
        put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, host)
        put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
        put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
    }
    return KafkaProducer(props)
}

private fun KafkaContainer.createTopic(topicName: String): Container.ExecResult {
    // kafka container uses with embedded zookeeper
    // confluent platform and Kafka compatibility 5.1.x <-> kafka 2.1.x
    // kafka 2.1.x require option --zookeeper, later versions use --bootstrap-servers instead
    val createTopic =
        "/usr/bin/kafka-topics --create " +
            "--zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic $topicName"
    return this.exec(createTopic)
}

private fun KafkaContainer.exec(command: String): Container.ExecResult {
    try {
        val execResult = this.execInContainer("/bin/sh", "-c", command)
        if (execResult.getExitCode() != 0)
            throw RuntimeException()
        return execResult
    } catch (e: Exception) {
        throw RuntimeException()
    }
}

private fun createKafkaProducer(bootstrapServers: List<String>): KafkaProducer<String, String> {
    val props = Properties().apply {
        put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
        put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
        put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
    }
    return KafkaProducer(props)
}
