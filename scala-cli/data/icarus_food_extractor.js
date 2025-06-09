const clean = (x) => x.replaceAll("\\n", "").trim();

const tableBody = document.querySelector(
  "#mw-content-text > div.mw-content-ltr.mw-parser-output > table.sortable.fandom-table.jquery-tablesorter > tbody"
);

const rows = tableBody.children;

JSON.stringify(
  Array.from(rows).map(
    (row) => {
      const cells = row.children;
      const foodItemCell = cells[0];
      const imgSrc =
        foodItemCell.children[0].children[0].children[0].src.toString();
      const foodName = foodItemCell.children[1].textContent;

      const foodValue = parseInt(cells[1].textContent);
      const waterValue = parseInt(cells[2].textContent);
      const buffDuration = cells[3].textContent;
      const spoilDuration = cells[4].textContent;

      const maxStamina = parseInt(cells[5].textContent);
      const staminaRegen = parseInt(cells[6].textContent);
      const maxHealth = parseInt(cells[7].textContent);
      const healthRegen = parseInt(cells[8].textContent);
      const experienceGain = cells[9].textContent;
      const misc = cells[10].textContent;
      return {
        foodName,
        foodValue,
        waterValue,
        buffDuration,
        spoilDuration,
        maxStamina,
        staminaRegen,
        maxHealth,
        healthRegen,
        experienceGain,
        misc,
        imgSrc,
      };
    },
    null,
    2
  )
);

const recipe = document.querySelector("#mw-content-text > div > table");
const recipeWorkbench = clean(recipe.children[0].textContent);
const recipeRows = recipe.children[1].children;

const recipeJSON = JSON.stringify({
  recipeWorkbench,
  ingredients: Array.from(recipeRows)
    .slice(1)
    .map(
      (row) => {
        const cells = row.children;

        const amount = parseInt(cells[1].textContent);
        const materialName = clean(cells[0].children[0].textContent);
        const imgSrc =
          cells[0].children[0].children[0].children[0].children[0].src.toString();
        return { amount, materialName, imgSrc };
      },
      null,
      2
    ),
});

console.log(recipeJSON);
