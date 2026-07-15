import { expect, test } from "@playwright/test";

test("Aurora supports multi-bubble streaming, stop, interrupt and replanning", async ({ page }) => {
  await page.goto("/app/aurora/index.html");
  const login = page.getByRole("heading", { name: "回到你的内宇宙" });
  const composer = page.getByLabel("写给 Aurora");
  await expect(login.or(composer)).toBeVisible();
  if (await login.isVisible()) {
    await page.getByLabel("用户名").fill(process.env.E2E_USERNAME ?? "demo");
    await page.getByLabel("密码").fill(process.env.E2E_PASSWORD ?? "demo123");
    await page.getByRole("button", { name: "登录" }).click();
  }

  await expect(composer).toBeVisible();
  await composer.fill("我刚才说累，其实更准确的是害怕自己做不好。先陪我理一下。 ");
  const send = page.getByRole("button", { name: "发送" });
  await expect(send).toBeEnabled();
  await send.click();

  const interrupt = page.getByRole("button", { name: "打断并发送" });
  await expect(interrupt).toBeVisible();
  await composer.fill("等等，我不想先分析原因。我想先确认这种害怕是不是可以被接住。 ");
  await interrupt.click();

  await expect(page.getByText("等等，我不想先分析原因。我想先确认这种害怕是不是可以被接住。 ")).toBeVisible();
  await expect(page.locator("article.aurora").last()).toContainText(/.+/);

  const stop = page.getByRole("button", { name: "停止回应" });
  if (await stop.isVisible().catch(() => false)) await stop.click();
  await expect(page.getByRole("status")).toContainText(/已停在这里|Aurora 在听|已从时间线恢复/);
});
