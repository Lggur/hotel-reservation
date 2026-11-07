pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "hotel-reservation"

include("eureka-server")
include("api-gateway")
include("hotel-service")
include("booking-service")
