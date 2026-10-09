package com.wobbuffe.api.pokemon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wobbuffe.api.integration.IntegrationControlService;
import com.wobbuffe.api.web.BadRequestException;
import com.wobbuffe.config.WobbuffeProperties;
import com.wobbuffe.core.IntegrationPolicy;
import com.wobbuffe.core.OperationEnvelope;
import com.wobbuffe.core.ResilienceExecutor;
import com.wobbuffe.core.ResilienceResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.ACCEPTED;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

@RestController
@RequestMapping("/api/pokemon")
public class PokemonController {
    private final PokemonService pokemonService;
    private final PokemonImportService importService;
    private final ResilienceExecutor resilienceExecutor;
    private final WobbuffeProperties properties;
    private final ObjectMapper objectMapper;
    private final IntegrationControlService integrationControl;

    public PokemonController(PokemonService pokemonService, PokemonImportService importService,
                             ResilienceExecutor resilienceExecutor, WobbuffeProperties properties,
                             ObjectMapper objectMapper, IntegrationControlService integrationControl) {
        this.pokemonService = pokemonService; this.importService = importService;
        this.resilienceExecutor = resilienceExecutor; this.properties = properties; this.objectMapper = objectMapper;
        this.integrationControl = integrationControl;
    }

    @PostMapping
    public ResponseEntity<PokemonResponse> create(@Valid @RequestBody CreatePokemonRequest request) {
        return ResponseEntity.status(CREATED).body(pokemonService.create(request));
    }

    @GetMapping
    public List<PokemonResponse> list() { return pokemonService.findAll(); }

    @GetMapping("/{id}")
    public PokemonResponse get(@PathVariable Long id) { return pokemonService.findById(id); }

    @PutMapping("/{id}")
    public PokemonResponse update(@PathVariable Long id, @Valid @RequestBody UpdatePokemonRequest request) {
        return pokemonService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        pokemonService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/import/{name}")
    public ResponseEntity<?> importPokemon(@PathVariable String name,
            @RequestHeader(name = "Idempotency-Key", required = false) String requestedIdempotencyKey,
            @RequestHeader(name = "X-Correlation-Id", required = false) String requestedCorrelationId) {
        String canonicalName = name.trim().toLowerCase(java.util.Locale.ROOT);
        if (!canonicalName.matches("[a-z0-9-]{1,80}")) throw new BadRequestException("Invalid Pokémon name");
        String operationId = UUID.randomUUID().toString();
        String idempotencyKey = optionalHeader(requestedIdempotencyKey, operationId, "Idempotency-Key");
        String correlationId = optionalHeader(requestedCorrelationId, operationId, "X-Correlation-Id");
        var payload = objectMapper.createObjectNode().put("name", canonicalName);
        // Contrato do starter: envelope versionado, política por integração e execução protegida ou enqueue forçado no modo de simulação.
        OperationEnvelope envelope = new OperationEnvelope(1, operationId, "pokemon.import", "pokeapi",
                Instant.now(), idempotencyKey, payload, correlationId);

        IntegrationPolicy policy = properties.policyFor("pokeapi");
        // Esta é a fronteira principal com a biblioteca: execute aplica retry/timeout/breaker; enqueue confirma o fallback SQS.
        ResilienceResult<ImportOutcome> result = integrationControl.isEnabled(IntegrationControlService.POKEAPI)
                ? resilienceExecutor.execute(policy, envelope, () -> importService.importByName(canonicalName))
                : resilienceExecutor.enqueue(policy, envelope);
        if (result instanceof ResilienceResult.Completed<ImportOutcome> completed) {
            ImportOutcome outcome = completed.value();
            return ResponseEntity.status(outcome.created() ? CREATED : OK).body(outcome.pokemon());
        }
        ResilienceResult.Queued<ImportOutcome> queued = (ResilienceResult.Queued<ImportOutcome>) result;
        String statusUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/operations/{id}").buildAndExpand(queued.operationId()).toUriString();
        return ResponseEntity.status(ACCEPTED).body(new ImportAcceptedResponse(queued.operationId(), "QUEUED", statusUrl));
    }

    private String optionalHeader(String value, String fallback, String headerName) {
        if (value == null || value.isBlank()) return fallback;
        if (value.length() > 160) throw new BadRequestException(headerName + " must be at most 160 characters");
        return value.trim();
    }
}
