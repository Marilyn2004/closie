import { test, expect } from "@playwright/test";
test("@demo complete sample collection → search → styling refinement → persistent favorites", async ({
  page,
}) => {
  const errors = [];
  page.on("pageerror", (e) => errors.push(e.message));
  await page.goto("/");
  await expect(page.getByRole("note")).toContainText("SAMPLE COLLECTION");
  await expect(page.locator(".product")).toHaveCount(24);
  await page.screenshot({
    path: "../.local/screenshots/demo-discover-desktop.png",
    fullPage: true,
  });
  await page.getByRole("button", { name: "Tops & knits", exact: true }).click();
  const cardigan = page.locator(".product").filter({
    has: page.getByRole("heading", {
      name: "The soft rib cardigan",
      exact: true,
    }),
  });
  await cardigan
    .getByRole("button", { name: "Save The soft rib cardigan", exact: true })
    .click();
  await cardigan
    .getByRole("button", { name: "Details for The soft rib cardigan" })
    .click();
  await expect(page.getByRole("dialog")).toContainText(
    "not a retailer listing",
  );
  await page.getByRole("button", { name: "Close product details" }).click();
  await page.getByRole("button", { name: "Find", exact: true }).click();
  await page
    .getByLabel("Describe what you want")
    .fill("Find me a black leather jacket under $100");
  await page.getByRole("button", { name: "Find it" }).click();
  await expect(page.locator(".product")).toHaveCount(1);
  await expect(page.locator(".product h3")).toHaveText(
    "The leather-look cropped jacket",
  );
  await page
    .getByLabel("Describe what you want")
    .fill("Something more feminine");
  await page.getByRole("button", { name: "Find it" }).click();
  await expect(page.locator(".context-line")).toContainText("Up to $100.00");
  await page.getByRole("button", { name: "Style", exact: true }).click();
  await page
    .getByLabel("Occasion, aesthetic & preferences")
    .fill(
      "I want an effortless feminine outfit for a coffee date with an Aritzia vibe. My budget is $150.",
    );
  await page.getByRole("button", { name: "Build my outfit" }).click();
  await expect(
    page.getByRole("heading", { name: "Consider it styled." }),
  ).toBeVisible();
  await expect(page.locator(".mode-label")).toContainText(
    "Rules-based stylist",
  );
  await expect(page.locator(".stylist-output .context-line")).toContainText(
    "coffee date",
  );
  await expect(page.locator(".stylist-output .context-line")).toContainText(
    "aritzia vibe",
  );
  const prices = await page
    .locator(".outfit-grid .product-meta strong")
    .allTextContents();
  const subtotal = prices.reduce(
    (sum, p) => sum + Number(p.replace(/[^0-9.]/g, "")),
    0,
  );
  expect(subtotal).toBeLessThanOrEqual(150);
  await expect(page.locator(".outfit-total")).toHaveText(
    `$${subtotal.toFixed(2)}`,
  );
  await expect(page.locator(".outfit-grid")).toContainText("Not for sale");
  await page
    .getByRole("button", {
      name: "Actually, make it suitable for a dinner date",
      exact: true,
    })
    .click();
  await expect(page.locator(".stylist-output .context-line")).toContainText(
    "dinner date",
  );
  await expect(page.locator(".stylist-output .context-line")).toContainText(
    "Up to $150.00",
  );
  await page.screenshot({
    path: "../.local/screenshots/demo-style-desktop.png",
    fullPage: true,
  });
  await page.getByRole("button", { name: "Saved", exact: true }).click();
  await expect(page.locator(".product h3")).toHaveText("The soft rib cardigan");
  await page.reload();
  await page.getByRole("button", { name: "Saved", exact: true }).click();
  await expect(page.locator(".product h3")).toHaveText("The soft rib cardigan");
  expect(errors).toEqual([]);
});
test("@demo mobile complete outfit and honest impossible budget", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");
  await page.getByRole("button", { name: "Style", exact: true }).click();
  await page
    .getByLabel("Occasion, aesthetic & preferences")
    .fill("a feminine college outfit under $120");
  await page.getByRole("button", { name: "Build my outfit" }).click();
  await expect(
    page.getByRole("heading", { name: "Consider it styled." }),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await page.screenshot({
    path: "../.local/screenshots/demo-style-mobile.png",
    fullPage: true,
  });
  await page.getByLabel("Total item budget (USD)").fill("1");
  await page.getByRole("button", { name: "Refine my outfit" }).click();
  await expect(
    page.getByRole("heading", { name: "A few pieces are missing." }),
  ).toBeVisible();
  await expect(page.locator(".outfit-total")).toHaveText("$0.00");
});
test("@demo natural-language euro budget keeps a single currency", async ({
  page,
}) => {
  await page.goto("/");
  await page.getByRole("button", { name: "Style", exact: true }).click();
  await page
    .getByLabel("Occasion, aesthetic & preferences")
    .fill("a feminine outfit for college under €100");
  await page.getByRole("button", { name: "Build my outfit" }).click();
  await expect(
    page.getByRole("heading", { name: "Consider it styled." }),
  ).toBeVisible();
  await expect(page.locator(".outfit-total")).toHaveText("€95.00");
  await expect(page.getByLabel("Total item budget (EUR)")).toBeVisible();
});
