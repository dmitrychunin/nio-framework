package integration

import integration.handler.InverseLetterCaseHandler
import integration.handler.PackagePayloadWithWorkerNameHandler
import integration.handler.ReplaceHandler
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.otus.framework.Router
import ru.otus.framework.el.ServerBootstrap
import ru.otus.framework.pipeline.ChannelPipeline
import java.lang.String.format
import kotlin.test.assertEquals

class IntegrationApiTest {

    companion object {
        private const val PORT = 8080
        private val url = format("http://%s:%s/some/url", "localhost", PORT)


    }
//todo make before all
    @BeforeEach
//        @JvmStatic
    fun beforeAll() {
//        START SERVER BASED ON FRAMEWORK
        val payloadPipeline = ChannelPipeline()
        payloadPipeline.addLast(InverseLetterCaseHandler())
        payloadPipeline.addLast(ReplaceHandler())
        payloadPipeline.addLast(PackagePayloadWithWorkerNameHandler())
        val payloadRouter = Router.route(payloadPipeline).path("/some")
        val routers = listOf(payloadRouter)

        val serverBootstrap = ServerBootstrap(routers, PORT, 2)
        serverBootstrap.startServer()
    }

    @Test
    fun shouldHandleClientHttpRequestAndReturnValidResponse() {
        val asString = given()
                .contentType(ContentType.TEXT)
                .body("testData_0")
                .queryParam("beforeReplace", "A")
                .queryParam("afterReplace", "Hey")
                .`when`()
                .post(url)
                .then()
                .statusCode(200)
                .extract().asString()
        assertTrue(asString.matches(Regex("^worker-\\d: echo: TESTdHeyTHey_0$")))
    }

    @Test
    fun shouldHandleExceptionIfRequiredAfterReplaceQueryParamIsMissing() {
        val asString = given()
                .contentType(ContentType.TEXT)
                .body("testData_0")
                .queryParam("beforeReplace", "A")
                .`when`()
                .post(url)
                .then()
                .statusCode(400)
                .extract().asString()
        assertEquals("Error on replacing beforeReplace to afterReplace", asString)
    }
}