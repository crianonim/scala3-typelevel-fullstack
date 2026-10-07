package com.crianonim.janscape

/** A verbatim copy of the TypeScript original's config/game.json, kept in the module that reads it:
  * the config schema, the validation and the numbers they check all come from one source. When the
  * original's file changes, the two are diffed by hand — [[GameConfig.parse]] catches anything this
  * copy gets wrong as an invalid config rather than a wrong game.
  */
object GameConfigJson:
  val raw: String =
    """{
  "play": {
    "fatigue": {
      "max": 10,
      "rollMax": 100,
      "exponent": 2
    },
    "rest": {
      "turns": 10
    }
  },
  "skills": {
    "xpPerLevelSquared": 10
  },
  "mining": {
    "swing": {
      "baseTurns": 2,
      "turnsPerTier": 1,
      "turnsPerPower": 1,
      "minTurns": 1,
      "xpPerMine": 1,
      "fatigue": 1
    },
    "ladder": {
      "chance": 0.1,
      "descendTurns": 20,
      "descendFatigue": 1
    },
    "fastTravel": {
      "everyLevels": 5
    },
    "depthFactor": {
      "levelsPerPoint": 5,
      "max": 3
    },
    "fatigueChance": {
      "base": 0.5,
      "perDepthFactorPoint": 0.1,
      "perTier": 0.75,
      "perPower": 0.25,
      "minFactor": 0.1
    },
    "nodes": {
      "min": 10,
      "max": 25
    },
    "materials": {
      "bandLevels": 5,
      "minShare": 0.05,
      "power": {
        "stone": 0,
        "coal": 0,
        "copper ore": 1,
        "tin ore": 2,
        "iron ore": 3,
        "silver ore": 4
      },
      "bands": [
        {
          "from": { "stone": 40, "coal": 14 },
          "to": { "stone": 40, "coal": 22 }
        },
        {
          "from": { "stone": 40, "coal": 22, "copper ore": 6 },
          "to": { "stone": 38, "coal": 19, "copper ore": 30 }
        },
        {
          "from": { "stone": 38, "coal": 19, "copper ore": 30, "tin ore": 6 },
          "to": { "stone": 36, "coal": 18, "copper ore": 8, "tin ore": 28 }
        },
        {
          "from": { "stone": 36, "coal": 18, "tin ore": 28, "iron ore": 6 },
          "to": { "stone": 34, "coal": 17, "tin ore": 8, "iron ore": 28 }
        },
        {
          "from": { "stone": 34, "coal": 17, "iron ore": 28, "silver ore": 8 },
          "to": { "stone": 33, "coal": 16, "iron ore": 18, "silver ore": 22 }
        },
        {
          "from": { "stone": 33, "coal": 16, "iron ore": 18, "silver ore": 22 }
        }
      ]
    }
  },
  "crafting": {
    "recipes": {
      "copper bar": {
        "level": 1,
        "inputs": { "copper ore": 2, "coal": 1 },
        "turns": 1
      },
      "bronze bar": {
        "level": 2,
        "inputs": { "copper bar": 1, "tin ore": 2, "coal": 1 },
        "turns": 2
      },
      "iron bar": {
        "level": 3,
        "inputs": { "iron ore": 2, "coal": 1 },
        "turns": 3
      },
      "copper pickaxe": {
        "level": 2,
        "inputs": { "copper bar": 10 },
        "turns": 5
      },
      "bronze pickaxe": {
        "level": 3,
        "inputs": { "bronze bar": 10 },
        "turns": 10
      },
      "stone axe": {
        "level": 1,
        "inputs": { "stone": 10 },
        "turns": 5
      },
      "copper axe": {
        "level": 2,
        "inputs": { "copper bar": 10 },
        "turns": 5
      },
      "bronze axe": {
        "level": 3,
        "inputs": { "bronze bar": 10 },
        "turns": 10
      }
    }
  },
  "equipment": {
    "slots": {
      "Mining Tool": {
        "stone pickaxe": 1,
        "copper pickaxe": 2,
        "bronze pickaxe": 3
      },
      "Forestry Tool": {
        "stone axe": 1,
        "copper axe": 2,
        "bronze axe": 3
      }
    }
  },
  "building": {
    "buildables": {
      "Furnace": {
        "inputs": { "stone": 25 },
        "turns": 50
      },
      "Basic Workbench": {
        "inputs": { "stone": 10 },
        "turns": 10
      },
      "Stone Anvil": {
        "inputs": { "stone": 20 },
        "turns": 10
      }
    }
  },
  "forest": {
    "glade": {
      "min": 10,
      "max": 25
    },
    "schedule": {
      "bandLevels": 5,
      "minShare": 0.05,
      "bands": [
        {
          "from": { "sticks": 40, "berries": 25 },
          "to": { "sticks": 40, "berries": 28 }
        },
        {
          "from": { "sticks": 40, "berries": 28, "ash tree": 6 },
          "to": { "sticks": 34, "berries": 24, "ash tree": 22 }
        },
        {
          "from": { "sticks": 34, "berries": 24, "ash tree": 22, "mushrooms": 6 },
          "to": { "sticks": 30, "berries": 22, "ash tree": 18, "mushrooms": 20 }
        },
        {
          "from": { "sticks": 30, "berries": 22, "ash tree": 18, "mushrooms": 20 }
        }
      ]
    },
    "resources": {
      "sticks": {
        "tier": 0,
        "turns": 1,
        "yield": { "min": 2, "max": 4 },
        "tiring": false
      },
      "berries": {
        "tier": 0,
        "turns": 1,
        "yield": { "min": 2, "max": 4 },
        "tiring": false
      },
      "ash tree": {
        "tier": 1,
        "turns": 4,
        "yield": { "min": 2, "max": 10 },
        "tiring": true
      },
      "mushrooms": {
        "tier": 2,
        "turns": 2,
        "yield": { "min": 2, "max": 4 },
        "tiring": false
      }
    },
    "path": {
      "chance": 0.1,
      "deeperTurns": 20,
      "deeperFatigue": 1
    },
    "fastTravel": {
      "everyLevels": 5
    },
    "depthFactor": {
      "levelsPerPoint": 5,
      "max": 3
    },
    "yieldFactor": {
      "perDepthFactorPoint": 1,
      "min": 1
    },
    "pathChance": {
      "base": 0.1,
      "perDepthFactorPoint": 0.05
    },
    "fatigueChance": {
      "base": 0.5,
      "perDepthFactorPoint": 0.1,
      "perTier": 0.75,
      "minFactor": 0.1
    },
    "chop": {
      "fatigue": 1
    }
  }
}
"""
