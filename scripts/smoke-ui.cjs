// Ejecutar únicamente contra una instancia de desarrollo: crea datos de prueba.
const { chromium } = require("playwright");
const assert = require("node:assert/strict");
const fs = require("node:fs");
(async () => {
  const local = fs.existsSync(".env")
    ? Object.fromEntries(
        fs
          .readFileSync(".env", "utf8")
          .split("\n")
          .filter((s) => s && !s.startsWith("#"))
          .map((s) => {
            const n = s.indexOf("=");
            return [s.slice(0, n), s.slice(n + 1)];
          }),
      )
    : {};
  const email = process.env.ADMIN_EMAIL || local.ADMIN_EMAIL;
  const password = process.env.ADMIN_PASSWORD || local.ADMIN_PASSWORD;
  assert(email && password, "Configura ADMIN_EMAIL y ADMIN_PASSWORD");
  const browser = await chromium.launch({
    headless: true,
    executablePath: process.env.CHROMIUM_PATH || undefined,
    args: ["--no-sandbox"],
  });
  try {
    const page = await browser.newPage({
      viewport: { width: 1440, height: 1000 },
    });
    const errors = [];
    page.on("pageerror", (e) => errors.push(e.message));
    await page.goto(process.env.BASE_URL || "http://127.0.0.1:8080");
    await page.locator("[name=email]").fill(email);
    await page.locator("[name=password]").fill(password);
    await page.locator("#auth-submit").click();
    await page.locator("#workspace").waitFor({ state: "visible" });
    const suffix = Date.now();
    const testPassword = require("node:crypto").randomBytes(24).toString("base64url");
    async function navigate(view) {
      await page.locator(`[data-view="${view}"]`).click();
      await page.locator("#new-record").waitFor();
      await page.locator("#new-record").click();
    }
    async function save() {
      await page.locator("#editor button[type=submit]").click();
      await page.locator("#modal").waitFor({ state: "hidden" });
    }
    await navigate("categories");
    await page.locator("#editor [name=name]").fill("E2E categoría " + suffix);
    await page.locator("#editor [name=description]").fill("Prueba de interfaz");
    await save();
    await navigate("suppliers");
    await page.locator("#editor [name=name]").fill("E2E proveedor " + suffix);
    await page.locator("#editor [name=email]").fill("proveedor@example.com");
    await page.locator("#editor [name=phone]").fill("3001234567");
    await save();
    await navigate("products");
    await page.locator("#editor [name=name]").fill("E2E producto " + suffix);
    await page.locator("#editor [name=sku]").fill("E2E-" + suffix);
    await page.locator("#editor [name=price]").fill("25900");
    await page.locator("#editor [name=stock]").fill("10");
    await page.locator("#editor [name=minimumStock]").fill("2");
    await page
      .locator("[name=categoryId]")
      .selectOption({ label: "E2E categoría " + suffix });
    await page
      .locator("[name=supplierId]")
      .selectOption({ label: "E2E proveedor " + suffix });
    await save();
    await navigate("sales");
    const product = page.locator(".sale-line select");
    const option = await product.locator("option").allTextContents();
    const index = option.findIndex((s) => s.includes("E2E producto " + suffix));
    const val = await product
      .locator("option")
      .nth(index)
      .getAttribute("value");
    await product.selectOption(val);
    await page.locator(".sale-line input").fill("2");
    await save();
    await page.locator("[data-view=products]").click();
    await page.locator("#search").waitFor();
    await page.locator("#search").fill("E2E-" + suffix);
    await page.waitForTimeout(100);
    assert.match(await page.locator("#records").innerText(), /8 uds/);
    await page.locator("[data-view=dashboard]").click();
    await page
      .locator("#page-title")
      .filter({ hasText: "Resumen general" })
      .waitFor();
    await page.locator(".stats").waitFor();
    await page.evaluate(
      () =>
        new Promise((resolve) =>
          requestAnimationFrame(() => requestAnimationFrame(resolve)),
        ),
    );
    fs.mkdirSync("test-results", { recursive: true });
    await page.screenshot({
      path: "test-results/dashboard-desktop.png",
      fullPage: true,
    });
    await page.setViewportSize({ width: 390, height: 844 });
    assert(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
      "El dashboard no debe desbordar en móvil",
    );
    await page.screenshot({
      path: "test-results/dashboard-mobile.png",
      fullPage: true,
    });
    await page.locator("#logout").click();
    await page.locator("#auth").waitFor({ state: "visible" });
    await page.locator("#toggle-auth").click();
    await page.locator("[name=name]").fill("E2E colaborador");
    await page.locator("[name=email]").fill(`e2e-${suffix}@example.com`);
    await page.locator("[name=password]").fill(testPassword);
    await page.locator("#auth-submit").click();
    await page
      .locator("#auth-title")
      .filter({ hasText: "Bienvenido de nuevo" })
      .waitFor();
    await page.locator("[name=email]").fill(`e2e-${suffix}@example.com`);
    await page.locator("[name=password]").fill(testPassword);
    await page.locator("#auth-submit").click();
    await page.locator("#workspace").waitFor({ state: "visible" });
    assert.equal(await page.locator("[data-view=users]").count(), 0);
    await page.locator("[data-view=products]").click();
    await page.locator("#search").waitFor();
    assert.equal(await page.locator("#new-record").count(), 0);
    await page.locator("#logout").click();
    await page.locator("#auth").waitFor({ state: "visible" });
    assert.deepEqual(errors, []);
    console.log(
      "PASS: login, catálogo relacionado, venta, stock, dashboard desktop/móvil, registro, permisos y logout.",
    );
  } finally {
    await browser.close();
  }
})().catch((e) => {
  console.error(e.message);
  process.exitCode = 1;
});
