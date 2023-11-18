package com.example

import jakarta.enterprise.context.ApplicationScoped
import org.apache.camel.builder.RouteBuilder

@ApplicationScoped
class PlanetaryConduitRoute: RouteBuilder() {
    override fun configure() {
        from("direct:planetoidSeven")
            .to("direct:waterPlanet")
    }
}