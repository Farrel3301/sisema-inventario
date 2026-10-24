"use strict";
const $ = (s) => document.querySelector(s);
let csrf,
  user,
  view = "dashboard",
  register = false,
  data = {};
const names = {
  dashboard: "Resumen general",
  products: "Productos",
  categories: "Categorías",
  suppliers: "Proveedores",
  sales: "Ventas",
  users: "Administración",
};
const icons = {
  dashboard: "◫",
  products: "▣",
  categories: "▦",
  suppliers: "♧",
  sales: "↗",
  users: "⚙",
};
const esc = (v) =>
  String(v ?? "").replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ],
  );
const money = (n) =>
  new Intl.NumberFormat("es-CO", { style: "currency", currency: "COP" }).format(
    n,
  );
function notify(message, error = false) {
  $("#toast").textContent = message;
  $("#toast").className = error ? "error" : "";
  $("#toast").hidden = false;
  clearTimeout(notify.timer);
  notify.timer = setTimeout(() => ($("#toast").hidden = true), 5000);
}
async function token() {
  csrf = await fetch("/api/csrf").then((r) => r.json());
}
async function api(path, method = "GET", body) {
  const headers = {};
  if (method !== "GET") {
    if (!csrf) await token();
    headers[csrf.headerName] = csrf.token;
    if (body !== undefined) headers["Content-Type"] = "application/json";
  }
  const response = await fetch("/api/" + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) {
    let result = await response.json().catch(() => ({}));
    if (response.status === 401 && path !== "login") {
      user = null;
      showAuth();
    }
    throw new Error(
      result.message ||
        (response.status === 403
          ? "No tienes permiso para esta operación"
          : response.status === 401
            ? "Correo o contraseña incorrectos"
            : "No se pudo completar la operación"),
    );
  }
  return response.status === 204
    ? null
    : await response.text().then((t) => (t ? JSON.parse(t) : null));
}
function showAuth() {
  $("#workspace").hidden = true;
  $("#auth").hidden = false;
}
$("#toggle-auth").onclick = () => {
  register = !register;
  $("#name-field").hidden = !register;
  $("#name-field input").required = register;
  $("#auth-title").textContent = register
    ? "Crea tu cuenta"
    : "Bienvenido de nuevo";
  $("#auth-submit").textContent = register
    ? "Crear cuenta →"
    : "Iniciar sesión →";
  $("#toggle-auth").textContent = register
    ? "Ya tengo cuenta. Iniciar sesión"
    : "¿No tienes cuenta? Regístrate";
  $("#auth-form [name=password]").minLength = register ? 12 : 1;
  $("#auth-form [name=password]").autocomplete = register
    ? "new-password"
    : "current-password";
};
$("#auth-form").onsubmit = async (e) => {
  e.preventDefault();
  const button = $("#auth-submit");
  button.disabled = true;
  try {
    const form = Object.fromEntries(new FormData(e.target));
    if (register) {
      await api("register", "POST", form);
      notify("Cuenta creada. Ya puedes iniciar sesión.");
      $("#toggle-auth").click();
      e.target.reset();
      return;
    }
    await token();
    const response = await fetch("/api/login", {
      method: "POST",
      headers: {
        [csrf.headerName]: csrf.token,
        "Content-Type": "application/x-www-form-urlencoded",
      },
      body: new URLSearchParams({
        username: form.email.trim(),
        password: form.password,
      }),
    });
    if (!response.ok) throw new Error("Correo o contraseña incorrectos");
    await token();
    e.target.reset();
    await boot();
  } catch (error) {
    notify(error.message, true);
  } finally {
    button.disabled = false;
  }
};
$("#logout").onclick = async () => {
  try {
    await api("logout", "POST");
    csrf = null;
    showAuth();
    await token();
  } catch (e) {
    notify(e.message, true);
  }
};
async function boot() {
  user = await api("me");
  $("#auth").hidden = true;
  $("#workspace").hidden = false;
  $("#user-name").textContent = user.name;
  $("#user-role").textContent =
    user.role === "ADMIN" ? "Administrador" : "Colaborador";
  $("#nav").innerHTML = Object.keys(names)
    .filter((k) => k !== "users" || user.role === "ADMIN")
    .map(
      (k) => `<button data-view="${k}">${icons[k]} &nbsp; ${names[k]}</button>`,
    )
    .join("");
  await load();
}
$("#nav").onclick = (e) => {
  const b = e.target.closest("[data-view]");
  if (b) {
    view = b.dataset.view;
    load().catch((e) => notify(e.message, true));
  }
};
async function load() {
  const keys = ["products", "categories", "suppliers", "sales"];
  if (user.role === "ADMIN") keys.push("users");
  const values = await Promise.all(keys.map((k) => api(k)));
  keys.forEach((k, i) => (data[k] = values[i]));
  render();
}
function empty(message = "Agrega tu primer registro para comenzar.") {
  return `<div class="empty"><strong>Todo listo para comenzar</strong>${esc(message)}</div>`;
}
function table(headers, rows) {
  return rows.length
    ? `<div class="table-wrap"><table><thead><tr>${headers.map((h) => `<th>${h}</th>`).join("")}</tr></thead><tbody>${rows.join("")}</tbody></table></div>`
    : empty();
}
function row(cells) {
  return `<tr>${cells.map((c) => `<td>${c}</td>`).join("")}</tr>`;
}
function actions(id) {
  return user.role === "ADMIN"
    ? `<div class="actions"><button data-edit="${id}">Editar</button><button class="danger" data-delete="${id}">Eliminar</button></div>`
    : "";
}
function productRows(products) {
  return products.map((p) =>
    row([
      `<strong>${esc(p.name)}</strong><br><small>${esc(p.sku)}</small>`,
      esc(p.category.name),
      esc(p.supplier.name),
      money(p.price),
      `<span class="pill ${p.stock <= p.minimumStock ? "low" : ""}">${p.stock} uds.</span>`,
      actions(p.id),
    ]),
  );
}
function render() {
  $("#page-title").textContent = names[view];
  document
    .querySelectorAll("[data-view]")
    .forEach((b) => b.classList.toggle("active", b.dataset.view === view));
  const admin = user.role === "ADMIN";
  if (view === "dashboard") {
    const low = data.products.filter((p) => p.stock <= p.minimumStock);
    const total = data.sales.reduce((a, s) => a + Number(s.total), 0);
    $("#content").innerHTML = `<section class="stats">${[
      [
        "▣",
        "Productos activos",
        data.products.length,
        "Referencias en tu catálogo",
      ],
      [
        "↗",
        "Ventas acumuladas",
        money(total),
        `${data.sales.length} operaciones registradas`,
      ],
      [
        "◈",
        "Valor del inventario",
        money(data.products.reduce((a, p) => a + p.stock * p.price, 0)),
        "Según precio de venta",
      ],
      ["⚑", "Alertas de stock", low.length, "Productos en nivel mínimo"],
    ]
      .map(
        (s) =>
          `<article class="stat"><span class="stat-icon">${s[0]}</span><p>${s[1]}</p><strong>${s[2]}</strong><small>${s[3]}</small></article>`,
      )
      .join(
        "",
      )}</section><section class="panel"><div class="panel-head"><div><h2>Productos que necesitan atención</h2><p class="muted">Mantén tus existencias al día.</p></div><button data-goto="products">Ver inventario →</button></div>${low.length ? table(["Producto", "Categoría", "Proveedor", "Precio", "Stock", ""], productRows(low)) : empty("No tienes productos con stock bajo.")}</section><section class="panel"><h2>Últimas ventas</h2>${salesTable([...data.sales].sort((a, b) => b.id - a.id).slice(0, 5))}</section>`;
    return;
  }
  $("#content").innerHTML =
    `<section class="panel"><div class="panel-head"><div><h2>${names[view]}</h2><p class="muted">${data[view].length} registros en tu espacio de trabajo</p></div>${view === "sales" || (admin && view !== "users") ? `<button class="primary" id="new-record">＋ ${view === "sales" ? "Registrar venta" : "Agregar"}</button>` : ""}</div><div class="toolbar"><input id="search" placeholder="Buscar en ${names[view].toLowerCase()}…" aria-label="Buscar"></div><div id="records"></div></section>`;
  renderRecords();
  $("#search").oninput = renderRecords;
  $("#new-record")?.addEventListener("click", () =>
    view === "sales" ? saleDialog() : editor(),
  );
}
function salesTable(sales) {
  return table(
    ["Venta", "Fecha", "Vendedor", "Productos", "Total", ""],
    sales.map((s) =>
      row([
        `<strong>#${String(s.id).padStart(4, "0")}</strong>`,
        esc(
          new Date(s.createdAt).toLocaleString("es-CO", {
            timeZone: "America/Bogota",
          }),
        ),
        esc(s.user.name),
        s.items.reduce((n, i) => n + i.quantity, 0),
        `<strong>${money(s.total)}</strong>`,
        `<button data-sale="${s.id}">Detalle</button>`,
      ]),
    ),
  );
}
function renderRecords() {
  const q = $("#search").value.toLowerCase();
  const items = data[view].filter((i) =>
    JSON.stringify(i).toLowerCase().includes(q),
  );
  let html;
  if (view === "products")
    html = table(
      [
        "Producto / SKU",
        "Categoría",
        "Proveedor",
        "Precio",
        "Stock",
        "Acciones",
      ],
      productRows(items),
    );
  if (view === "categories")
    html = table(
      ["Nombre", "Descripción", "Acciones"],
      items.map((i) =>
        row([
          `<strong>${esc(i.name)}</strong>`,
          esc(i.description),
          actions(i.id),
        ]),
      ),
    );
  if (view === "suppliers")
    html = table(
      ["Proveedor", "Correo", "Teléfono", "Acciones"],
      items.map((i) =>
        row([
          `<strong>${esc(i.name)}</strong>`,
          esc(i.email),
          esc(i.phone),
          actions(i.id),
        ]),
      ),
    );
  if (view === "sales") html = salesTable(items.sort((a, b) => b.id - a.id));
  if (view === "users")
    html = table(
      ["Nombre", "Correo", "Rol", "Administrar"],
      items.map((i) =>
        row([
          esc(i.name),
          esc(i.email),
          `<span class="pill">${esc(i.role)}</span>`,
          i.id === user.id
            ? "Tu cuenta"
            : `<button data-role="${i.id}" data-value="${i.role === "ADMIN" ? "USER" : "ADMIN"}">${i.role === "ADMIN" ? "Hacer colaborador" : "Hacer administrador"}</button>`,
        ]),
      ),
    );
  $("#records").innerHTML = html;
}
$("#content").onclick = async (e) => {
  const b = e.target.closest("button");
  if (!b) return;
  try {
    if (view === "dashboard" && (b.dataset.edit || b.dataset.delete)) {
      view = "products";
      render();
    }
    if (b.dataset.goto) {
      view = b.dataset.goto;
      render();
    }
    if (b.dataset.edit)
      editor(data[view].find((i) => i.id === Number(b.dataset.edit)));
    if (
      b.dataset.delete &&
      confirm(
        "¿Eliminar este registro? Las referencias usadas en ventas no se pueden eliminar.",
      )
    ) {
      await api(view + "/" + b.dataset.delete, "DELETE");
      await load();
      notify("Registro eliminado");
    }
    if (
      b.dataset.role &&
      confirm(
        "¿Confirmas el cambio de rol? Se aplicará en el próximo inicio de sesión.",
      )
    ) {
      await api("users/" + b.dataset.role + "/role", "PUT", {
        role: b.dataset.value,
      });
      await load();
      notify("Rol actualizado");
    }
    if (b.dataset.sale) {
      const s = data.sales.find((i) => i.id === Number(b.dataset.sale));
      openModal(
        "Detalle de venta #" + s.id,
        `${table(
          ["Producto", "Cantidad", "Precio unitario", "Subtotal"],
          s.items.map((i) =>
            row([
              esc(i.productName),
              i.quantity,
              money(i.unitPrice),
              money(i.unitPrice * i.quantity),
            ]),
          ),
        )}<p class="sale-total">Total: ${money(s.total)}</p>`,
      );
    }
  } catch (error) {
    notify(error.message, true);
  }
};
function openModal(title, html) {
  $("#modal-title").textContent = title;
  $("#editor").innerHTML = html;
  $("#editor").onsubmit = (e) => e.preventDefault();
  $("#modal").showModal();
}
$("#close-modal").onclick = () => $("#modal").close();
function editor(item = {}) {
  const specs =
    view === "products"
      ? [
          ["name", "Nombre", "text", 150],
          ["sku", "SKU", "text", 60],
          ["price", "Precio de venta", "number"],
          ["stock", "Existencias", "number"],
          ["minimumStock", "Stock mínimo", "number"],
          ["categoryId", "Categoría", "select", data.categories],
          ["supplierId", "Proveedor", "select", data.suppliers],
        ]
      : view === "categories"
        ? [
            ["name", "Nombre", "text", 100],
            ["description", "Descripción", "text", 255],
          ]
        : [
            ["name", "Nombre", "text", 100],
            ["email", "Correo", "email", 150],
            ["phone", "Teléfono", "text", 40],
          ];
  if (
    view === "products" &&
    (!data.categories.length || !data.suppliers.length)
  ) {
    notify("Crea primero una categoría y un proveedor.", true);
    return;
  }
  openModal(
    `${item.id ? "Editar" : "Agregar"} ${names[view].toLowerCase()}`,
    `<div class="form-grid">${specs
      .map(([key, label, type, extra]) => {
        const value =
          item[key] ??
          (key === "categoryId"
            ? item.category?.id
            : key === "supplierId"
              ? item.supplier?.id
              : "");
        return `<label>${label}${type === "select" ? `<select name="${key}" required>${extra.map((o) => `<option value="${o.id}" ${Number(value) === o.id ? "selected" : ""}>${esc(o.name)}</option>`).join("")}</select>` : `<input name="${key}" type="${type}" value="${esc(value)}" ${["name", "sku", "price", "stock", "minimumStock"].includes(key) ? "required" : ""} ${type === "number" ? `min="${key === "price" ? ".01" : "0"}" max="${key === "price" ? "999999999.99" : "1000000"}" step="${key === "price" ? ".01" : "1"}"` : `maxlength="${extra}"`}>`}</label>`;
      })
      .join(
        "",
      )}</div><button class="primary" type="submit">Guardar cambios</button>`,
  );
  $("#editor").onsubmit = async (e) => {
    e.preventDefault();
    const b = e.submitter;
    b.disabled = true;
    try {
      const values = Object.fromEntries(new FormData(e.target));
      if (view === "products")
        ["price", "stock", "minimumStock", "categoryId", "supplierId"].forEach(
          (k) => (values[k] = Number(values[k])),
        );
      await api(
        view + (item.id ? "/" + item.id : ""),
        item.id ? "PUT" : "POST",
        values,
      );
      $("#modal").close();
      await load();
      notify("Registro guardado");
    } catch (error) {
      notify(error.message, true);
    } finally {
      b.disabled = false;
    }
  };
}
function saleDialog() {
  const available = data.products.filter((p) => p.stock > 0);
  if (!available.length) {
    notify("No hay productos disponibles para vender.", true);
    return;
  }
  openModal(
    "Registrar venta",
    `<p class="muted">El stock se actualizará al confirmar la venta.</p><div id="sale-lines"></div><button type="button" id="add-line">＋ Agregar producto</button><p class="sale-total" id="sale-total"></p><button class="primary" type="submit">Confirmar venta</button>`,
  );
  const update = () => {
    $("#sale-total").textContent =
      "Total: " +
      money(
        [...document.querySelectorAll(".sale-line")].reduce(
          (sum, line) =>
            sum +
            Number(
              available.find(
                (p) => p.id === Number(line.querySelector("select").value),
              ).price,
            ) *
              Number(line.querySelector("input").value),
          0,
        ),
      );
  };
  const add = () => {
    const line = document.createElement("div");
    line.className = "sale-line";
    line.innerHTML = `<select aria-label="Producto">${available.map((p) => `<option value="${p.id}">${esc(p.name)} · ${money(p.price)} · Stock ${p.stock}</option>`).join("")}</select><input aria-label="Cantidad" type="number" min="1" max="${available[0].stock}" value="1" required><button type="button" aria-label="Quitar">×</button>`;
    line.querySelector("select").onchange = () => {
      line.querySelector("input").max = available.find(
        (p) => p.id === Number(line.querySelector("select").value),
      ).stock;
      update();
    };
    line.querySelector("input").oninput = update;
    line.querySelector("button").onclick = () => {
      line.remove();
      update();
    };
    $("#sale-lines").append(line);
    update();
  };
  $("#add-line").onclick = add;
  add();
  $("#editor").onsubmit = async (e) => {
    e.preventDefault();
    const b = e.submitter;
    b.disabled = true;
    try {
      const items = [...document.querySelectorAll(".sale-line")].map((l) => ({
        productId: Number(l.querySelector("select").value),
        quantity: Number(l.querySelector("input").value),
      }));
      if (!items.length) throw new Error("Agrega al menos un producto");
      await api("sales", "POST", { items });
      $("#modal").close();
      await load();
      notify("Venta registrada correctamente");
    } catch (error) {
      notify(error.message, true);
    } finally {
      b.disabled = false;
    }
  };
}
(async () => {
  try {
    await token();
    const response = await fetch("/api/me");
    if (response.ok) await boot();
    else if (response.status !== 401)
      notify("El servidor no está disponible.", true);
  } catch (error) {
    notify("No se pudo conectar con el servidor.", true);
  }
})();
