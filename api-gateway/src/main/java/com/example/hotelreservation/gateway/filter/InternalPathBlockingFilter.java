package com.example.hotelreservation.gateway.filter;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class InternalPathBlockingFilter implements GlobalFilter, Ordered {

    private static final Pattern INTERNAL_PATH = Pattern.compile(
            "^(/internal(/.*)?|/api/rooms/\\d+/(confirm-availability|release))$");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!INTERNAL_PATH.matcher(exchange.getRequest().getPath().value()).matches()) {
            return chain.filter(exchange);
        }

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.NOT_FOUND);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

        byte[] body = """
                {"title":"Not found","status":404,"detail":"Resource not found"}
                """.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
