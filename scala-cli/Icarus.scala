// filepath: /Users/jan/git/scala3-typelevel-fullstack/scala-cli/Icarus.scala
//> using dep net.ruippeixotog::scala-scraper:3.1.3

import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL.Parse._

case class Ingredent(name: String, amount: Int)

case class Recipe(name: String, workbench: String, ingredients: List[Ingredent])

def ingre1 = {
  val browser = JsoupBrowser()
  val doc = browser.parseFile("data/bio.html")
  val name = doc >> ".mw-page-title-main" >> text
  val recipe = doc >>  "#mw-content-text" >> "div" >> "table"
  val recipeRows = recipe >> elementList("tr")
  val ingredients = recipeRows.map { row =>
    val cells = row >> elementList("td")
    if (cells.length == 2) {
      val name = cells(0) >> text
      val amount = cells(1) >> text
      Some(Ingredent(name, amount.toInt))
    } else {
      None
    }
  }.flatten
  val recipeWorkbench = recipe >> element("caption") >> text
  println(name)
  println(recipeWorkbench)
  println(ingredients)
}

def consumableLinks = {
  val browser = JsoupBrowser()
  val doc = browser.parseFile("data/consumables2.html")
  val consumableLinks = doc >> "#mw-content-text > div.category-page__members" >> elementList("li > a ").map(a => a >> attr("href") )
  consumableLinks.map(link => {
    println(s"curl https://icarus.fandom.com$link -o ${link.drop(1).filter(x=> x.isLetter || x == '/')}.html")
  })
}

def craftingBenches = {
  val browser = JsoupBrowser()
  val doc = browser.parseFile("data/crafting_benches.html")
//   val consumableLinks = doc >> "#mw-content-text > div.category-page__members" >> text
  val consumableLinks = doc >> "#mw-content-text > div.category-page__members" >> elementList("li > a ").map(a => a >> attr("href") )
  
//   println(consumableLinks)
  consumableLinks.map(link => {
    println(s"curl https://icarus.fandom.com$link -o ${link.drop(1).filter(x=> x.isLetter || x == '/')}.html")
  })
}

@main def main(): Unit = {
  craftingBenches
}