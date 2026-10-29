import { test, expect } from "@playwright/test";
import path from "node:path";
test.skip(
  !process.env.CLOSIE_STUDIO_BROWSER,
  "Studio tests require the isolated studio backend.",
);
const fixture = path.resolve(
  "../src/main/resources/static/concepts/pink-cardigan.png",
);
test("Discover → save → edit → version → style preserves anchor @studio", async ({
  page,
}) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: /Your next/ })).toBeVisible();
  await expect(page.locator(".concept-card")).toHaveCount(6);
  await page
    .getByRole("button", { name: "Save cardigan", exact: true })
    .click();
  await page.getByRole("button", { name: "Saved", exact: true }).click();
  await expect(page.locator(".concept-card")).toHaveCount(1);
  await page
    .locator(".concept-card")
    .first()
    .getByRole("button", { name: /View / })
    .click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toBeVisible();
  await dialog.getByLabel("Refine concept").fill("make it lavender");
  await dialog
    .getByRole("button", { name: "Save a new design version ↗" })
    .click();
  await expect(dialog.getByRole("heading", { name: /lavender/ })).toBeVisible();
  await expect(
    dialog.getByText("Image not generated", { exact: true }),
  ).toBeVisible();
  await dialog
    .getByRole("button", { name: "Generate this version's image ✧" })
    .click();
  await expect(page.getByRole("alert")).toContainText("generation is off");
  await page.getByRole("button", { name: "Close concept" }).click();
  await page.getByRole("button", { name: "Discover", exact: true }).click();
  await page.getByRole("button", { name: "Tops & knits", exact: true }).click();
  await expect(page.locator(".concept-card")).toHaveCount(3);
  await page
    .getByRole("button", { name: "Style cardigan", exact: true })
    .first()
    .click();
  await expect(
    page.getByRole("heading", { name: "Built around your favorite." }),
  ).toBeVisible();
  const before = await page.locator(".board-piece").allTextContents();
  await page
    .locator(".slot-BOTTOM")
    .getByRole("button", { name: "Change this piece ↗" })
    .click();
  await page
    .locator(".replacement-picker button")
    .filter({ hasText: "skirt" })
    .click();
  await expect(page.locator(".slot-BOTTOM")).toContainText("skirt");
  await expect(page.locator(".slot-TOP")).toContainText(
    "Your anchor · preserved",
  );
  expect(
    (await page.locator(".slot-TOP").textContent()).includes("cardigan"),
  ).toBe(true);
  await page.getByRole("button", { name: "Try a doll preview ✧" }).click();
  await expect(page.getByRole("alert")).toContainText("generation is off");
});
test("Find has several persistent directions and restores a revision @studio", async ({
  page,
}) => {
  await page.goto("/");
  await page.getByRole("button", { name: "Find", exact: true }).click();
  await page
    .getByLabel("Describe your style")
    .fill("Lavender something cute for class");
  await page.getByRole("button", { name: "Explore ↗" }).click();
  await expect(page.locator(".concept-card")).toHaveCount(3);
  await page
    .locator(".concept-card")
    .first()
    .getByRole("button", { name: /View / })
    .click();
  await page.getByLabel("Refine concept").fill("make it pink");
  await page
    .getByRole("button", { name: "Save a new design version ↗" })
    .click();
  await expect(
    page.getByLabel("Version history").locator("option"),
  ).toHaveCount(2);
  await page.getByLabel("Version history").selectOption({ index: 0 });
  await page.getByRole("button", { name: "Restore this version" }).click();
  await expect(
    page.getByLabel("Version history").locator("option"),
  ).toHaveCount(3);
  await page.getByRole("button", { name: "Close concept" }).click();
  await page.reload();
  await page.getByRole("button", { name: "Discover", exact: true }).click();
  await expect(page.locator(".concept-card")).toHaveCount(9);
});
test("All Style My Item inputs converge on confirmation @studio", async ({
  page,
}) => {
  await page.goto("/");
  await page.getByRole("button", { name: "Style", exact: true }).click();
  await page.getByLabel("Garment photo").setInputFiles(fixture);
  await expect(page.getByAltText("Your private garment photo")).toBeVisible();
  await page
    .getByLabel("Describe your garment")
    .fill("my pink fitted cardigan");
  await page.getByRole("button", { name: "Identify & review ↗" }).click();
  await expect(
    page.getByRole("heading", { name: "Does this look right?" }),
  ).toBeVisible();
  await expect(page.locator(".confirmation")).toContainText("NOT FOUND");
  await page
    .getByRole("button", { name: "Confirm my item & style it ↗" })
    .click();
  await expect(page.locator(".slot-TOP")).toContainText(
    "Your anchor · preserved",
  );
  await page
    .getByRole("button", { name: "Brand / model", exact: true })
    .click();
  await page
    .getByLabel("Brand, product name, model / SKU and color")
    .fill("Aritzia Super Puff Shorty black");
  await page.getByRole("button", { name: "Identify & review ↗" }).click();
  await expect(page.locator(".confirmation")).toContainText("NOT FOUND");
  await page
    .getByRole("button", { name: "Confirm my item & style it ↗" })
    .click();
  await page.getByRole("button", { name: "Product link", exact: true }).click();
  await page
    .getByLabel("Retailer product link")
    .fill("https://example.com/unavailable-garment");
  await page.getByLabel("Describe your garment").fill("cream jacket");
  await page.getByRole("button", { name: "Identify & review ↗" }).click();
  await expect(page.locator(".confirmation")).toContainText("NOT FOUND");
  await page
    .getByRole("button", { name: "Confirm my item & style it ↗" })
    .click();
  await expect(page.locator(".closet-list article")).toHaveCount(3);
  await page
    .locator(".closet-list article")
    .first()
    .getByRole("button", { name: "Delete item", exact: true })
    .click();
  await expect(page.locator(".closet-list article")).toHaveCount(2);
});
test("Mobile capsule and preferences fit the viewport @studio", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");
  await expect(page.locator(".concept-card")).toHaveCount(6);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await page.screenshot({
    path: "../docs/screenshots/studio-mobile.png",
    fullPage: true,
  });
  await page.getByRole("button", { name: "My Style", exact: true }).click();
  await page.getByRole("button", { name: "Pastel Cozy", exact: true }).click();
  await page.getByLabel("Favorite colors").fill("blue");
  await page
    .getByRole("button", { name: "Save my style", exact: true })
    .click();
  await page.getByRole("button", { name: "Discover", exact: true }).click();
  await expect(page.locator(".concept-card").first()).toContainText("hoodie");
});
test("Desktop visual snapshot @studio", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1100 });
  await page.goto("/");
  await expect(page.locator(".concept-card")).toHaveCount(6);
  await page.screenshot({
    path: "../docs/screenshots/studio-discover.png",
    fullPage: true,
  });
});
