package ink.abb.pogo.api.cache

import POGOProtos.Data.Player.PlayerCurrencyOuterClass.PlayerCurrency
import POGOProtos.Data.Player.PlayerStatsOuterClass
import POGOProtos.Data.Player.PlayerStatsOuterClass.PlayerStats
import POGOProtos.Data.PokedexEntryOuterClass
import POGOProtos.Enums.PokemonFamilyIdOuterClass.PokemonFamilyId
import POGOProtos.Enums.PokemonIdOuterClass
import POGOProtos.Inventory.AppliedItemOuterClass
import POGOProtos.Inventory.EggIncubatorOuterClass.EggIncubator
import POGOProtos.Inventory.InventoryDeltaOuterClass
import POGOProtos.Inventory.Item.ItemIdOuterClass.ItemId

class Inventory {
    val items = mutableMapOf<ItemId, Int>()
    val pokemon = mutableSetOf<BagPokemon>()
    var appliedItems = mutableListOf<AppliedItemOuterClass.AppliedItem>()
    val candies = mutableMapOf<PokemonFamilyId, Int>()

    var eggIncubators = mutableListOf<EggIncubator>()

    var size: Int = 350

    var gems: Int = 0

    lateinit var playerStats: PlayerStats

    var pokedex = mutableMapOf<PokemonIdOuterClass.PokemonId, PokedexEntryOuterClass.PokedexEntry>()

    fun update(inventoryDelta: InventoryDeltaOuterClass.InventoryDelta) {
        if (inventoryDelta.originalTimestampMs == 0L) {
            inventoryDelta.inventoryItemsList.forEach {
                val itemData = it.inventoryItemData
                if (itemData.hasAppliedItems()) {
                    this.appliedItems = itemData.appliedItems.itemList
                }
                if (itemData.hasCandy()) {
                    this.candies.set(itemData.candy.familyId, itemData.candy.candy)
                }
                if (itemData.hasEggIncubators()) {
                    this.eggIncubators = itemData.eggIncubators.eggIncubatorList
                }
                if (itemData.hasInventoryUpgrades()) {
                    var total = 350
                    itemData.inventoryUpgrades.inventoryUpgradesList.forEach {
                        total += it.additionalStorage
                    }
                    size = total
                }
                if (itemData.hasItem()) {
                    items.set(itemData.item.itemId, itemData.item.count)
                }
                if (itemData.hasPlayerCurrency()) {
                    // ???
                    this.gems = itemData.playerCurrency.gems
                }
                if (itemData.hasPlayerStats()) {
                    this.playerStats = itemData.playerStats
                }
                if (itemData.hasPokedexEntry()) {
                    pokedex.set(itemData.pokedexEntry.pokemonId, itemData.pokedexEntry)
                }
                if (itemData.hasPokemonData()) {
                    pokemon.add(BagPokemon(itemData.pokemonData))
                }
            }
        }
    }
}