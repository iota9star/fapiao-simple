package io.nichijou.utils.fp

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.*
import io.ktor.gson.gson
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.request.path
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.slf4j.event.Level

fun main() {
  embeddedServer(Netty, 8080) {
    module()
  }.start(wait = true)
}

@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
  install(Compression) {
    gzip {
      priority = 1.0
    }
    deflate {
      priority = 10.0
      minimumSize(1024) // condition
    }
  }

  install(CallLogging) {
    level = Level.INFO
    filter { call -> call.request.path().startsWith("/") }
  }

  install(CORS) {
    method(HttpMethod.Options)
    method(HttpMethod.Put)
    method(HttpMethod.Delete)
    method(HttpMethod.Patch)
    header(HttpHeaders.Authorization)
    allowCredentials = true
    anyHost()
  }

  install(ContentNegotiation) {
    gson {
    }
  }
  routing {
    post("/vc") {
      val params = call.receive<VerCodeParams>()
      val ver = Fapiao.getInstance().getVerCode(params)
      if (ver == null) {
        call.respond(HttpStatusCode.InternalServerError)
      } else {
        call.respond(ver)
      }
    }

    post("/fp") {
      val params = call.receive<FapiaoParams>()
      val resp = Fapiao.getInstance().getFapiao(params)
      if (resp == null) {
        call.respond(HttpStatusCode.InternalServerError)
      } else {
        call.respond(resp)
      }
    }
    get("/fi") {
      val fpdm = call.parameters["fpdm"]
      if (fpdm.isNullOrBlank()) {
        call.respond(HttpStatusCode.InternalServerError)
      } else {
        val area = Fapiao.getFapiaoArea(fpdm)
        val type = Fapiao.getFapiaoType(fpdm)
        call.respond(hashMapOf("area" to area, "type" to type))
      }
    }
  }
}

