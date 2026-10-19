package es.fervor.goagain.app.health

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthTest {

    @Test
    fun `health returns 200`() = testApplication {
        configure()

        assertEquals(HttpStatusCode.OK, client.get("/health").status)
    }

    @Test
    fun `health body is status OK`() = testApplication {
        configure()

        assertEquals("{\"status\":\"OK\"}", client.get("/health").bodyAsText())
    }

    @Test
    fun `health responds with JSON content type`() = testApplication {
        configure()

        assertEquals(ContentType.Application.Json, client.get("/health").contentType())
    }
}