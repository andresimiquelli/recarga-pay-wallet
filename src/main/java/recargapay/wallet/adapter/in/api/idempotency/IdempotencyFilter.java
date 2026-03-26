package recargapay.wallet.adapter.in.api.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.util.ContentCachingResponseWrapper;
import recargapay.wallet.application.exception.IdempotencyRequestInProgressException;
import recargapay.wallet.application.idempotency.IdempotencyDecision;
import recargapay.wallet.application.idempotency.IdempotencyRecord;
import recargapay.wallet.application.service.IdempotencyService;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IdempotencyFilter extends OncePerRequestFilter {
    private final IdempotencyService idempotencyService;
    private final CanonicalRequestHasher canonicalRequestHasher;
    private final HandlerExceptionResolver handlerExceptionResolver;

    public IdempotencyFilter(
            IdempotencyService idempotencyService,
            CanonicalRequestHasher canonicalRequestHasher,
            HandlerExceptionResolver handlerExceptionResolver) {
        this.idempotencyService = idempotencyService;
        this.canonicalRequestHasher = canonicalRequestHasher;
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        byte[] requestBody = request.getInputStream().readAllBytes();
        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request, requestBody);
        String idempotencyKey = request.getHeader(IdempotencyService.IDEMPOTENCY_HEADER);
        String requestHash = canonicalRequestHasher.hash(
                request.getMethod(),
                request.getRequestURI(),
                request.getQueryString(),
                requestBody);

        IdempotencyDecision decision;
        try {
            decision = idempotencyService.begin(idempotencyKey, requestHash);
        } catch (RuntimeException exception) {
            handlerExceptionResolver.resolveException(request, response, null, exception);
            return;
        }

        if (decision.type() == IdempotencyDecision.Type.REPLAY) {
            writeStoredResponse(response, decision.record());
            return;
        }

        if (decision.type() == IdempotencyDecision.Type.IN_PROGRESS) {
            handlerExceptionResolver.resolveException(
                    request,
                    response,
                    null,
                    new IdempotencyRequestInProgressException("request with this Idempotency-Key is already processing"));
            return;
        }

        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
            storeResponse(idempotencyKey, wrappedResponse);
        } catch (Exception exception) {
            storeFailure(idempotencyKey);
            throw exception;
        } finally {
            wrappedResponse.copyBodyToResponse();
        }
    }

    private void writeStoredResponse(HttpServletResponse response, IdempotencyRecord record) throws IOException {
        response.setStatus(record.getResponseStatus());
        if (record.getResponseContentType() != null && !record.getResponseContentType().isBlank()) {
            response.setContentType(record.getResponseContentType());
        }
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        if (record.getResponseBody() != null) {
            response.getWriter().write(record.getResponseBody());
        }
    }

    private void storeResponse(String idempotencyKey, ContentCachingResponseWrapper response) {
        byte[] responseBody = response.getContentAsByteArray();
        idempotencyService.complete(
                idempotencyKey,
                response.getStatus(),
                response.getContentType(),
                new String(responseBody, StandardCharsets.UTF_8));
    }

    private void storeFailure(String idempotencyKey) {
        idempotencyService.complete(idempotencyKey, 500, MediaType.APPLICATION_JSON_VALUE, "");
    }
}
