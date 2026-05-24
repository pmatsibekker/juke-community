import { test, expect } from '@playwright/test';

test('test', async ({ page }) => {
await page.routeFromHAR('har/first_interaction.har', {update:true,url:'**/greeting*'});
  await page.goto('http://localhost:3000/');
  await page.locator('#inputName').click();
  await page.locator('#inputName').fill('Evan');
  await page.getByRole('button', { name: 'Submit' }).click();
 await page.waitForTimeout(1500);
 //await expect( page.locator('#response')).toHaveText('Hello, Evan!');
  await page.locator('#inputName').fill('Jack');
  await page.getByRole('button', { name: 'Submit' }).click();
  await page.waitForTimeout(1500);
  //await expect( page.locator('#response')).toHaveText('Hello, Jack!');
});