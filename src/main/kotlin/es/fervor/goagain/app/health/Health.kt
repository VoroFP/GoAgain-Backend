package es.fervor.goagain.app.health

import es.fervor.goagain.app.health.StatusType.OK
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureHealth() {
    routing {
        get("/health") {
            call.respond(HealthDTO(status = OK))
        }
    }
}
