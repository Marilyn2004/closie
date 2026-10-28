import { test, expect } from "@playwright/test";
test.skip(
  !process.env.CLOSIE_PUBLIC_BROWSER,
  "Needs the isolated public-demo backend.",
);
test("Public Discover → Find → Style → Saved @public", async ({ page }) => {
  await page.goto("/?retail=1");
  await expect(page.locator(".notice").first()).toContainText("Public demo");
  await expect(page.locator(".concept-card")).toHaveCount(7);
  await page.getByLabel("Style collection").selectOption("Playful Y2K");
  await expect(page.locator(".concept-card")).toHaveCount(2);
  await page.getByLabel("Style collection").selectOption("");
  await page.getByRole("button", { name: "Find", exact: true }).click();
  await page
    .getByLabel("Describe your style")
    .fill("a fitted pink cardigan for class");
  await page.getByRole("button", { name: "Explore ↗" }).click();
  await expect(page.locator(".concept-card")).toHaveCount(1);
  await expect(page.locator(".concept-card")).toContainText("pink");
  await page
    .getByRole("button", { name: "Save cardigan", exact: true })
    .click();
  await page
    .getByRole("button", { name: "Style cardigan", exact: true })
    .click();
  await expect(page.locator(".slot-TOP")).toContainText("Your anchor");
  await page.getByRole("button", { name: "♡ Save look", exact: true }).click();
  await page
    .locator(".slot-BOTTOM")
    .getByRole("button", { name: "Change this piece ↗" })
    .click();
  await page
    .locator(".replacement-picker button")
    .filter({ hasText: "skirt" })
    .click();
  await expect(page.locator(".slot-BOTTOM")).toContainText("skirt");
  await page.getByRole("button", { name: "Saved", exact: true }).click();
  await expect(page.locator(".concept-card")).toHaveCount(1);
  await expect(
    page.getByRole("heading", { name: "Saved looks" }),
  ).toBeVisible();
  await page.reload();
  await page.getByRole("button", { name: "Saved", exact: true }).click();
  await expect(page.locator(".concept-card")).toHaveCount(1);
});
test("Public search empty and refinement are honest @public", async ({
  page,
}) => {
  await page.goto("/");
  await page.getByLabel("Describe your style").fill("green hoodie");
  await page.getByRole("button", { name: "Explore ↗" }).click();
  await expect(page.locator(".concept-card")).toHaveCount(0);
  await expect(
    page.locator(".notice").filter({ hasText: "No existing concept" }),
  ).toContainText("No existing concept");
  await page.getByLabel("Describe your style").fill("blue cardigan");
  await page.getByRole("button", { name: "Explore ↗" }).click();
  await expect(page.locator(".concept-card")).toHaveCount(1);
  await page
    .getByRole("button", { name: "View baby blue cardigan", exact: true })
    .click();
  await expect(page.getByRole("dialog")).toContainText("Pre-generated AI");
  await expect(
    page.getByRole("button", { name: "Delete concept & generated images" }),
  ).toHaveCount(0);
  await expect(
    page.getByRole("button", { name: "Generate this version's image ✧" }),
  ).toHaveCount(0);
  await page.getByLabel("Refine concept").fill("make it pink");
  await page.getByRole("button", { name: "Search this refinement ↗" }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(page.locator(".concept-card")).toHaveCount(1);
  await expect(page.locator(".concept-card")).toContainText("pink");
});
test("Sample outfits and limited Style My Item on mobile @public", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");
  await expect(page.locator(".concept-card")).toHaveCount(7);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await page.screenshot({
    path: "../docs/screenshots/public-demo-mobile.png",
    fullPage: true,
  });
  await page.getByRole("button", { name: "Style", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Upload a photo", exact: true }),
  ).toBeDisabled();
  await expect(page.locator(".owned-form")).toContainText(
    "unavailable in this public demo",
  );
  await page
    .getByRole("button", { name: /^View The campus coffee run/ })
    .click();
  await expect(page.locator(".outfit-board")).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Try a doll preview ✧" }),
  ).toHaveCount(0);
  await page
    .locator(".slot-BOTTOM")
    .getByRole("button", { name: "Change this piece ↗" })
    .click();
  await page
    .locator(".replacement-picker button")
    .filter({ hasText: "skirt" })
    .click();
  await expect(page.locator(".slot-BOTTOM")).toContainText("skirt");
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await page.screenshot({
    path: "../docs/screenshots/public-demo-outfit.png",
    fullPage: true,
  });
});
test("Public desktop snapshot @public", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1100 });
  await page.goto("/");
  await expect(page.locator(".concept-card")).toHaveCount(7);
  await page.screenshot({
    path: "../docs/screenshots/public-demo-discover.png",
    fullPage: true,
  });
});
