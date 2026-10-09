const api = "/api";
const pokemonList = document.querySelector("#pokemon-list");
const emptyState = document.querySelector("#empty-state");
const form = document.querySelector("#pokemon-form");
const importForm = document.querySelector("#import-form");
const operationForm = document.querySelector("#operation-form");
const toast = document.querySelector("#toast");
const integrationToggle = document.querySelector("#integration-toggle");
const integrationToggleLabel = document.querySelector("#integration-toggle-label");
const integrationState = document.querySelector("#integration-state");
const integrationHelp = document.querySelector("#integration-control-help");
let pokemon = [];
let toastTimer;
let pokeApiEnabled = null;

async function request(path, options = {}) {
  const response = await fetch(`${api}${path}`, {
    ...options,
    headers: { ...(options.body ? { "Content-Type": "application/json" } : {}), ...(options.headers || {}) }
  });
  const contentType = response.headers.get("content-type") || "";
  const body = response.status === 204 ? null : contentType.includes("json") ? await response.json() : await response.text();
  if (!response.ok) {
    const message = body && typeof body === "object" ? body.detail || body.message : body;
    throw new Error(message || `A solicitação falhou (${response.status}).`);
  }
  return { status: response.status, body };
}

function showToast(message, error = false) {
  toast.textContent = message;
  toast.classList.toggle("error", error);
  toast.classList.add("show");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.remove("show"), 3400);
}

function setBusy(button, busy, busyLabel = "Enviando…") {
  if (busy) {
    button.dataset.label = button.innerHTML;
    button.innerHTML = busyLabel;
    button.disabled = true;
  } else {
    button.innerHTML = button.dataset.label || button.innerHTML;
    button.disabled = false;
  }
}

function pokemonColor(name) {
  const colors = ["#edf5e6|#54895e", "#f4edf9|#9170a4", "#fff1e8|#bd7d50", "#e9f3f5|#588795", "#f8f1e4|#a17c47", "#edf0fc|#707bab"];
  const score = [...name].reduce((sum, letter) => sum + letter.charCodeAt(0), 0);
  return colors[score % colors.length].split("|");
}

function formatMeasure(value, unit) {
  if (value === null || value === undefined) return "—";
  const measure = (value / 10).toLocaleString("pt-BR", { maximumFractionDigits: 1 });
  return `${measure} ${unit}`;
}

function renderPokemon() {
  const query = document.querySelector("#search").value.trim().toLocaleLowerCase("pt-BR");
  const visible = pokemon.filter((item) => item.name.toLocaleLowerCase("pt-BR").includes(query));
  pokemonList.replaceChildren();
  for (const item of visible) {
    const row = document.createElement("tr");
    const [background, foreground] = pokemonColor(item.name);
    const pokemonCell = document.createElement("td");
    const pokemonWrap = document.createElement("div");
    pokemonWrap.className = "pokemon-cell";
    const avatar = document.createElement("span");
    avatar.className = "pokemon-avatar";
    avatar.style.background = background;
    avatar.style.color = foreground;
    avatar.textContent = item.name.slice(0, 1);
    const identity = document.createElement("div");
    const name = document.createElement("div");
    name.className = "pokemon-name";
    name.textContent = item.name;
    const id = document.createElement("div");
    id.className = "pokemon-sub";
    id.textContent = `Registro #${item.id}`;
    identity.append(name, id);
    pokemonWrap.append(avatar, identity);
    pokemonCell.append(pokemonWrap);
    const dex = document.createElement("td");
    dex.textContent = item.pokedexId ? `#${String(item.pokedexId).padStart(3, "0")}` : "—";
    const height = document.createElement("td");
    height.textContent = formatMeasure(item.height, "m");
    const weight = document.createElement("td");
    weight.textContent = formatMeasure(item.weight, "kg");
    const actions = document.createElement("td");
    const actionWrap = document.createElement("div");
    actionWrap.className = "row-actions";
    const edit = document.createElement("button");
    edit.type = "button";
    edit.className = "row-action";
    edit.title = `Editar ${item.name}`;
    edit.setAttribute("aria-label", `Editar ${item.name}`);
    edit.textContent = "✎";
    edit.addEventListener("click", () => editPokemon(item));
    const remove = document.createElement("button");
    remove.type = "button";
    remove.className = "row-action delete";
    remove.title = `Excluir ${item.name}`;
    remove.setAttribute("aria-label", `Excluir ${item.name}`);
    remove.textContent = "×";
    remove.addEventListener("click", () => deletePokemon(item));
    actionWrap.append(edit, remove);
    actions.append(actionWrap);
    row.append(pokemonCell, dex, height, weight, actions);
    pokemonList.append(row);
  }

  const count = pokemon.length;
  document.querySelector("#stat-count").textContent = count;
  document.querySelector("#heading-count").textContent = count;
  document.querySelector("#nav-count").textContent = count;
  emptyState.classList.toggle("hidden", visible.length > 0);
  document.querySelector("#list-summary").textContent = query
    ? `${visible.length} resultado${visible.length === 1 ? "" : "s"} para “${query}”`
    : `${count} registro${count === 1 ? "" : "s"} na coleção`;
}

async function loadPokemon() {
  try {
    const result = await request("/pokemon");
    pokemon = result.body;
    renderPokemon();
  } catch (error) {
    showToast(`Não foi possível carregar a coleção: ${error.message}`, true);
    document.querySelector("#list-summary").textContent = "Falha ao carregar a coleção";
  }
}

async function checkHealth() {
  const pill = document.querySelector("#health-pill");
  const label = pill.querySelector("span");
  try {
    const response = await fetch("/actuator/health");
    if (!response.ok) throw new Error("API indisponível");
    const health = await response.json();
    const online = health.status === "UP";
    pill.classList.toggle("offline", !online);
    label.textContent = online ? "API conectada" : "API com atenção";
    document.querySelector("#stat-api").textContent = online ? "Operacional" : "Atenção";
  } catch {
    pill.classList.add("offline");
    label.textContent = "API indisponível";
    document.querySelector("#stat-api").textContent = "Offline";
  }
}

function renderIntegrationState() {
  integrationState.classList.remove("loading", "connected", "disconnected", "unknown");
  if (pokeApiEnabled === null) {
    integrationState.classList.add("unknown");
    integrationState.querySelector("span").textContent = "Estado indisponível";
    integrationToggleLabel.textContent = "Tentar novamente";
    integrationToggle.setAttribute("aria-checked", "false");
    integrationToggle.setAttribute("aria-label", "Consultar estado da integração com PokéAPI");
    integrationHelp.textContent = "Não foi possível consultar o estado atual da integração.";
    return;
  }

  integrationState.classList.add(pokeApiEnabled ? "connected" : "disconnected");
  integrationState.querySelector("span").textContent = pokeApiEnabled ? "Chamada direta ativa" : "Instabilidade simulada";
  integrationToggleLabel.textContent = pokeApiEnabled ? "Simular instabilidade" : "Retomar chamada direta";
  integrationToggle.setAttribute("aria-checked", String(pokeApiEnabled));
  integrationToggle.setAttribute("aria-label", pokeApiEnabled ? "Simular instabilidade na integração" : "Retomar chamada direta à PokéAPI");
  integrationHelp.textContent = pokeApiEnabled
    ? "Novos imports consultam a PokéAPI diretamente. Ative a simulação para testar o desvio para a fila."
    : "Instabilidade simulada: novos imports são enviados à fila; o worker continua consultando a PokéAPI real.";
}

async function loadIntegrationState() {
  integrationToggle.disabled = true;
  try {
    const { body } = await request("/integrations/pokeapi");
    pokeApiEnabled = body.enabled;
  } catch {
    pokeApiEnabled = null;
  }
  renderIntegrationState();
  integrationToggle.disabled = false;
}

async function toggleIntegration() {
  if (pokeApiEnabled === null) {
    await loadIntegrationState();
    return;
  }

  // Este controle orienta o controller a usar execute() direto ou enqueue() do Wobbuffe Starter.
  const nextEnabled = !pokeApiEnabled;
  integrationToggle.disabled = true;
  integrationToggleLabel.textContent = "Atualizando…";
  try {
    const { body } = await request("/integrations/pokeapi", {
      method: "PUT",
      body: JSON.stringify({ enabled: nextEnabled })
    });
    pokeApiEnabled = body.enabled;
    showToast(pokeApiEnabled
      ? "Chamada direta restaurada."
      : "Instabilidade simulada. Novos imports serão enfileirados para o worker.");
  } catch (error) {
    showToast(`Não foi possível alterar a integração: ${error.message}`, true);
  } finally {
    renderIntegrationState();
    integrationToggle.disabled = false;
  }
}

function editPokemon(item) {
  document.querySelector("#pokemon-id").value = item.id;
  document.querySelector("#name").value = item.name;
  document.querySelector("#pokedexId").value = item.pokedexId ?? "";
  document.querySelector("#height").value = item.height;
  document.querySelector("#weight").value = item.weight;
  document.querySelector("#form-title").textContent = `Editar ${item.name}`;
  document.querySelector("#save-button").firstChild.textContent = "Atualizar Pokémon ";
  document.querySelector("#cancel-edit").classList.remove("hidden");
  document.querySelector("#adicionar").scrollIntoView({ behavior: "smooth", block: "center" });
  document.querySelector("#name").focus({ preventScroll: true });
}

function resetPokemonForm() {
  form.reset();
  document.querySelector("#pokemon-id").value = "";
  document.querySelector("#form-title").textContent = "Adicionar Pokémon";
  document.querySelector("#save-button").firstChild.textContent = "Salvar Pokémon ";
  document.querySelector("#cancel-edit").classList.add("hidden");
}

async function deletePokemon(item) {
  if (!window.confirm(`Excluir ${item.name} da coleção?`)) return;
  try {
    await request(`/pokemon/${item.id}`, { method: "DELETE" });
    await loadPokemon();
    showToast(`${item.name} foi removido da coleção.`);
  } catch (error) {
    showToast(error.message, true);
  }
}

function displayOperation(operation) {
  const result = document.querySelector("#operation-result");
  result.classList.remove("hidden", "error");
  result.replaceChildren();
  const top = document.createElement("div");
  top.className = "result-top";
  const id = document.createElement("span");
  id.className = "result-id";
  id.textContent = operation.operationId;
  const status = document.createElement("span");
  status.className = `status-tag${operation.status === "FAILED" ? " failed" : ""}`;
  status.textContent = operation.status;
  top.append(id, status);
  const meta = document.createElement("p");
  meta.className = "operation-meta";
  meta.textContent = `${operation.operationType} · ${operation.integrationId} · ${new Date(operation.updatedAt).toLocaleString("pt-BR")}`;
  result.append(top, meta);
}

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  const button = document.querySelector("#save-button");
  const id = document.querySelector("#pokemon-id").value;
  const payload = {
    name: document.querySelector("#name").value.trim(),
    pokedexId: document.querySelector("#pokedexId").value ? Number(document.querySelector("#pokedexId").value) : null,
    height: Number(document.querySelector("#height").value),
    weight: Number(document.querySelector("#weight").value)
  };
  setBusy(button, true);
  try {
    await request(id ? `/pokemon/${id}` : "/pokemon", { method: id ? "PUT" : "POST", body: JSON.stringify(payload) });
    resetPokemonForm();
    await loadPokemon();
    showToast(id ? `${payload.name} foi atualizado.` : `${payload.name} entrou para a coleção!`);
  } catch (error) {
    showToast(error.message, true);
  } finally {
    setBusy(button, false);
  }
});

importForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const button = importForm.querySelector("button");
  const name = document.querySelector("#import-name").value.trim().toLocaleLowerCase("pt-BR");
  setBusy(button, true, "…");
  try {
    const { status, body } = await request(`/pokemon/import/${encodeURIComponent(name)}`, {
      method: "POST",
      headers: {
        "Idempotency-Key": crypto.randomUUID(),
        "X-Correlation-Id": crypto.randomUUID()
      }
    });
    if (status === 202) {
      document.querySelector("#operation-id").value = body.operationId;
      document.querySelector("#operation-result").classList.remove("hidden", "error");
      document.querySelector("#operation-result").replaceChildren();
      const top = document.createElement("div");
      top.className = "result-top";
      const id = document.createElement("span");
      id.className = "result-id";
      id.textContent = body.operationId;
      const tag = document.createElement("span");
      tag.className = "status-tag";
      tag.textContent = body.status;
      top.append(id, tag);
      const meta = document.createElement("p");
      meta.className = "operation-meta";
      meta.textContent = "Importação aceita e publicada na fila.";
      document.querySelector("#operation-result").append(top, meta);
      showToast("Importação aceita para processamento assíncrono.");
    } else {
      await loadPokemon();
      showToast(`${body.name} foi importado com sucesso!`);
    }
    importForm.reset();
  } catch (error) {
    showToast(error.message, true);
  } finally {
    setBusy(button, false);
  }
});

operationForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const button = operationForm.querySelector("button");
  const id = document.querySelector("#operation-id").value.trim();
  setBusy(button, true, "Consultando…");
  try {
    const { body } = await request(`/operations/${encodeURIComponent(id)}`);
    displayOperation(body);
  } catch (error) {
    const result = document.querySelector("#operation-result");
    result.classList.remove("hidden");
    result.classList.add("error");
    result.textContent = error.message;
  } finally {
    setBusy(button, false);
  }
});

document.querySelector("#search").addEventListener("input", renderPokemon);
document.querySelector("#refresh-button").addEventListener("click", loadPokemon);
document.querySelector("#cancel-edit").addEventListener("click", resetPokemonForm);
document.querySelector("#empty-add").addEventListener("click", () => {
  document.querySelector("#adicionar").scrollIntoView({ behavior: "smooth", block: "center" });
  document.querySelector("#name").focus({ preventScroll: true });
});
integrationToggle.addEventListener("click", toggleIntegration);

loadPokemon();
checkHealth();
loadIntegrationState();
