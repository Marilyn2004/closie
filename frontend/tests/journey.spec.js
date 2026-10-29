import { test, expect } from "@playwright/test";
// Actual Spring HTTP endpoints + isolated PostgreSQL fixtures. No API mocking or live retailer claims.
test("Discover → Find → conversational refinement → Style", async ({
  page,
}) => {
  const errors = [];
  page.on("pageerror", (e) => errors.push(e.message));
  await page.route("https://example.test/**", (route) =>
    route.fulfill({
      contentType: "image/svg+xml",
      body: '<svg xmlns="http://www.w3.org/2000/svg" width="300" height="400"><rect width="300" height="400" fill="#e5e5dc"/><text x="25" y="200" font-size="20">TEST CATALOG FIXTURE</text></svg>',
    }),
  );
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "Meet your next favorites." }),
  ).toBeVisible();
  await expect(page.locator(".product")).toHaveCount(5);
  await page.screenshot({
    path: "test-results/discover-desktop.png",
    fullPage: true,
  });
  await page
    .getByRole("button", { name: "Save Fixture ivory blouse", exact: true })
    .click();
  await expect(
    page.getByRole("button", {
      name: "Unsave Fixture ivory blouse",
      exact: true,
    }),
  ).toHaveAttribute("aria-pressed", "true");
  await page.getByRole("button", { name: "Find", exact: true }).click();
  await page
    .getByLabel("Describe what you want")
    .fill("I want a minimalist outfit for college under $150");
  await page.getByRole("button", { name: "Find it" }).click();
  await expect(page.locator(".product")).toHaveCount(4);
  await page
    .getByLabel("Describe what you want")
    .fill("Something more feminine");
  await page.getByRole("button", { name: "Find it" }).click();
  await expect(page.locator(".context-line")).toContainText(
    "feminine · college · Up to $150.00",
  );
  const product = page.locator(".product").filter({
    has: page.getByRole("heading", {
      name: "Fixture ivory blouse",
      exact: true,
    }),
  });
  await expect(
    product.getByRole("link", { name: "View at Test retailer" }),
  ).toHaveAttribute("href", /^https:\/\/example.test\/products\//);
  await product.getByRole("button", { name: "Style this" }).click();
  await page
    .getByLabel("Occasion, aesthetic & preferences")
    .fill("A feminine look for college");
  await page
    .getByRole("button", { name: /Build my outfit|Refine my outfit/ })
    .click();
  await expect(
    page.getByRole("heading", { name: "Consider it styled." }),
  ).toBeVisible();
  await expect(page.locator(".outfit-total")).toHaveText("$120.00");
  await expect(page.locator(".outfit-grid .product")).toHaveCount(3);
  await page.screenshot({
    path: "test-results/style-desktop.png",
    fullPage: true,
  });
  await page.getByLabel("Total item budget (USD)").fill("10");
  await page
    .getByRole("button", { name: /Build my outfit|Refine my outfit/ })
    .click();
  await expect(page.getByRole("alert")).toContainText(
    "Anchor currency or price",
  );
  expect(errors).toEqual([]);
});
test("mobile navigation, empty and error states", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");
  await page
    .getByRole("button", { name: "Jewelry & extras", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Nothing here just yet." }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Find", exact: true }).click();
  await page.getByLabel("Describe what you want").fill("under $1");
  await page.getByRole("button", { name: "Find it" }).click();
  await expect(
    page.getByRole("heading", { name: "Nothing here just yet." }),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
});
