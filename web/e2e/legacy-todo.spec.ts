import { expect, test } from "@playwright/test";

async function loginIfNeeded(page: import("@playwright/test").Page) {
  const login = page.getByRole("heading", { name: "回到你的内宇宙" });
  const appShell = page.getByRole("navigation", { name: "Inner Cosmos 五个空间" });
  await expect(login.or(appShell)).toBeVisible();
  if (await login.isVisible()) {
    await page.getByLabel("用户名").fill(process.env.E2E_USERNAME ?? "demo");
    await page.getByLabel("密码").fill(process.env.E2E_PASSWORD ?? "demo123");
    await page.getByRole("button", { name: "登录" }).click();
  }
  await expect(appShell).toBeVisible();
  const offlineNotice = page.getByRole("button", { name: "知道了" });
  if (await offlineNotice.isVisible().catch(() => false)) await offlineNotice.click();
}

async function gotoCosmos(page: import("@playwright/test").Page) {
  await page.goto("/app/aurora/index.html");
  await loginIfNeeded(page);
  await page.getByRole("button", { name: /^内宇宙/ }).click();
  await expect(page.getByRole("heading", { name: "待办清单" })).toBeVisible();
}

test("seeded todos are visible in the cosmos space", async ({ page }) => {
  await gotoCosmos(page);
  const todoList = page.locator(".todo-list");
  await expect(todoList.getByText("验证 Aurora 是否真实调用 MiniMax").first()).toBeVisible();
  await expect(todoList.getByText("整理考试范围第一章").first()).toBeVisible();
  await page.screenshot({ path: "test-results/legacy-todo-seeded.png", fullPage: true });
});

test("create a todo, split it into subtasks, and change its status", async ({ page }) => {
  await gotoCosmos(page);

  const stamp = Date.now().toString(36);
  const taskName = `拆第一步测试-${stamp}`;
  await page.getByLabel("任务名称").fill(taskName);
  await page.getByRole("button", { name: "添加待办" }).click();

  const todoList = page.locator(".todo-list");
  await expect(todoList.getByText(taskName)).toBeVisible();
  await page.screenshot({ path: "test-results/legacy-todo-created.png", fullPage: true });

  const card = todoList.locator(".todo-card", { hasText: taskName });

  // Split -- calls POST /api/todos/{id}/split on the real backend.
  await card.getByRole("button", { name: "拆第一步" }).click();
  await expect(todoList.getByText(taskName)).toBeVisible();

  // Mark as done.
  await card.getByRole("button", { name: "完成" }).click();

  // Verify in the Done tab.
  await page.getByRole("tab", { name: "已完成" }).click();
  await expect(page.locator(".todo-list").getByText(taskName)).toBeVisible();
  await page.screenshot({ path: "test-results/legacy-todo-done.png", fullPage: true });
});

test("editing a todo updates its task name", async ({ page }) => {
  await gotoCosmos(page);

  const stamp = Date.now().toString(36);
  const originalName = `待编辑-${stamp}`;
  const editedName = `已编辑-${stamp}`;
  await page.getByLabel("任务名称").fill(originalName);
  await page.getByRole("button", { name: "添加待办" }).click();

  const todoList = page.locator(".todo-list");
  await expect(todoList.getByText(originalName)).toBeVisible();

  // Click edit on the card that contains the unique name.
  await todoList.locator(".todo-card", { hasText: originalName })
    .getByRole("button", { name: "编辑" }).click();

  // After clicking edit, an inline edit form replaces the card content.
  // Only one edit form can be open at a time, so target it directly.
  const editForm = page.locator(".todo-edit-form");
  await editForm.getByLabel("任务名称").fill(editedName);
  await editForm.getByRole("button", { name: "保存修改" }).click();

  await expect(todoList.getByText(editedName)).toBeVisible();
  await page.screenshot({ path: "test-results/legacy-todo-edited.png", fullPage: true });
});
