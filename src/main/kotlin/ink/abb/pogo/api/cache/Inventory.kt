package ink.abb.pogo.api.cache

import POGOProtos.Data.Player.PlayerStatsOuterClass.PlayerStats
import POGOProtos.Data.PokedexEntryOuterClass
import POGOProtos.Enums.PokemonFamilyIdOuterClass.PokemonFamilyId
import POGOProtos.Enums.PokemonIdOuterClass
import POGOProtos.Inventory.AppliedItemOuterClass
import POGOProtos.Inventory.EggIncubatorOuterClass.EggIncubator
import POGOProtos.Inventory.InventoryDeltaOuterClass
import POGOProtos.Inventory.Item.ItemIdOuterClass.ItemId
import ink.abb.pogo.api.PoGoApi
import java.util.concurrent.atomic.AtomicInteger

class Inventory(val poGoApi: PoGoApi) {
    var currencies = mutableMapOf<String, AtomicInteger>()
    var items = mutableMapOf<ItemId, AtomicInteger>()
    var pokemon = mutableMapOf<Long, BagPokemon>()
    var eggs = mutableMapOf<Long, BagPokemon>()
    var appliedItems = mutableListOf<AppliedItemOuterClass.AppliedItem>()
    var candies = mutableMapOf<PokemonFamilyId, AtomicInteger>()

    var eggIncubators = mutableMapOf<String, EggIncubator.Builder>()

    var maxSize = AtomicInteger(350)

    var gems = AtomicInteger(0)

    lateinit var playerStats: PlayerStats

    var pokedex = mutableMapOf<PokemonIdOuterClass.PokemonId, PokedexEntryOuterClass.PokedexEntry>()

    val hasPokeballs: Boolean
        get() {
            var totalCount = 0
            setOf(ItemId.ITEM_POKE_BALL, ItemId.ITEM_GREAT_BALL, ItemId.ITEM_MASTER_BALL, ItemId.ITEM_ULTRA_BALL).forEach {
                totalCount += items.getOrPut(it, { AtomicInteger(0) }).get()
            }
            return totalCount > 0
        }

    val size: Int
        get() {
            return items.map { it.value.get() }.fold(0, { a: Int, b: Int -> a + b })
        }

    fun update(inventoryDelta: InventoryDeltaOuterClass.InventoryDelta) {
        val items: MutableMap<ItemId, AtomicInteger>
        val pokemon: MutableMap<Long, BagPokemon>
        val eggs: MutableMap<Long, BagPokemon>
        val eggIncubators: MutableMap<String, EggIncubator.Builder>
        val candies: MutableMap<PokemonFamilyId, AtomicInteger>

        val maxSize: AtomicInteger

        val gems: AtomicInteger

        val pokedex: MutableMap<PokemonIdOuterClass.PokemonId, PokedexEntryOuterClass.PokedexEntry>

        if (inventoryDelta.originalTimestampMs == 0L) {
            items = mutableMapOf<ItemId, AtomicInteger>()
            pokemon = mutableMapOf<Long, BagPokemon>()
            eggs = mutableMapOf<Long, BagPokemon>()
            eggIncubators = mutableMapOf()
            candies = mutableMapOf<PokemonFamilyId, AtomicInteger>()

            maxSize = AtomicInteger(350)

            gems = AtomicInteger(0)

            pokedex = mutableMapOf<PokemonIdOuterClass.PokemonId, PokedexEntryOuterClass.PokedexEntry>()
        } else {
            items = this.items
            pokemon = this.pokemon
            eggs = this.eggs
            candies = this.candies
            maxSize = this.maxSize
            gems = this.gems
            pokedex = this.pokedex
            eggIncubators = this.eggIncubators
        }
        inventoryDelta.inventoryItemsList.forEach {
            val itemData = it.inventoryItemData
            if (itemData.hasAppliedItems()) {
                this.appliedItems = itemData.appliedItems.itemList
            }
            if (itemData.hasCandy()) {
                candies.getOrPut(itemData.candy.familyId, { AtomicInteger(0) }).set(itemData.candy.candy)
            }
            if (itemData.hasEggIncubators()) {
                itemData.eggIncubators.eggIncubatorList.forEach { eggIncubators.put(it.id, EggIncubator.newBuilder(it)) }
            }
            if (itemData.hasInventoryUpgrades()) {
                var total = 350
                itemData.inventoryUpgrades.inventoryUpgradesList.forEach {
                    total += it.additionalStorage
                }
                maxSize.set(total)
            }
            if (itemData.hasItem()) {
                items.getOrPut(itemData.item.itemId, { AtomicInteger(0) }).set(itemData.item.count)
            }
            if (itemData.hasPlayerCurrency()) {
                // ???
                gems.set(itemData.playerCurrency.gems)
            }
            if (itemData.hasPlayerStats()) {
                playerStats = itemData.playerStats
            }
            if (itemData.hasPokedexEntry()) {
                pokedex.set(itemData.pokedexEntry.pokemonId, itemData.pokedexEntry)
            }
            if (itemData.hasPokemonData()) {
                if (itemData.pokemonData.isEgg) {
                    eggs.put(itemData.pokemonData.id, BagPokemon(poGoApi, itemData.pokemonData))
                } else {
                    pokemon.put(itemData.pokemonData.id, BagPokemon(poGoApi, itemData.pokemonData))
                }
            }
        }
        if (inventoryDelta.originalTimestampMs == 0L) {
            this.items = items
            this.pokemon = pokemon
            this.eggs = eggs
            this.eggIncubators = eggIncubators
            this.candies = candies
            this.pokedex = pokedex
        }
    }
}