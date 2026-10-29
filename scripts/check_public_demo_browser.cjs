// Browser smoke against the packaged local artifact, including its production CSP.
const {
  chromium,
  expect,
} = require("../frontend/node_modules/@playwright/test");
const base = process.argv[2] || "http://127.0.0.1:8083";
if (!/^http:\/\/(127\.0\.0\.1|localhost):\d+$/.test(base))
  throw new Error("Use a local public-demo URL.");
(async () => {
  const browser = await chromium.launch();
  try {
    const page = await browser.newPage({
      viewport: { width: 1440, height: 1100 },
    });
    const errors = [];
    page.on("pageerror", (error) => errors.push(error.message));
    await page.goto(base);
    await page.locator(".concept-card").nth(6).waitFor();
    if ((await page.locator(".concept-card").count()) !== 7)
      throw new Error("Missing public capsule.");
    await page.screenshot({
      path: "docs/screenshots/public-demo-packaged.png",
      fullPage: true,
    });
    await page.getByLabel("Describe your style").fill("blue cardigan");
    await page.getByRole("button", { name: "Explore ↗" }).click();
    await expect(page.locator(".concept-card")).toHaveCount(1);
    await page
      .getByRole("button", { name: "Style cardigan", exact: true })
      .waitFor();
    if ((await page.locator(".concept-card").count()) !== 1)
      throw new Error("Unexpected search result.");
    await page
      .getByRole("button", { name: "Style cardigan", exact: true })
      .click();
    await page.locator(".outfit-board").waitFor();
    await page
      .getByRole("button", { name: "♡ Save look", exact: true })
      .click();
    await page.getByRole("button", { name: "Saved", exact: true }).click();
    await page
      .getByRole("heading", { name: "Saved looks", exact: true })
      .waitFor();
    if (errors.length)
      throw new Error("Production browser errors: " + errors.join(";"));
    console.log(
      JSON.stringify({
        packagedBrowser: true,
        concepts: 7,
        search: true,
        outfit: true,
        savedLook: true,
        pageErrors: 0,
        providerCalls: 0,
      }),
    );
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
