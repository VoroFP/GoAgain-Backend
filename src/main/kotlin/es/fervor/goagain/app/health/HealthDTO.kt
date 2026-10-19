package es.fervor.goagain.app.health

import kotlinx.serialization.Serializable

@Serializable
data class HealthDTO(val status: StatusType)

enum class StatusType {
    OK,
}
