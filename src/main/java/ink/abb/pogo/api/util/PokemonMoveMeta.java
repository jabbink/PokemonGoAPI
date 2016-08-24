package ink.abb.pogo.api.util;

import POGOProtos.Enums.PokemonMoveOuterClass.PokemonMove;
import lombok.Getter;
import lombok.Setter;

public class PokemonMoveMeta {

    @Getter
    @Setter
    private PokemonMove move;
    @Getter
    @Setter
    private PokemonType type;
    @Getter
    @Setter
    private int power;
    @Getter
    @Setter
    private int accuracy;
    @Getter
    @Setter
    private double critChance;
    @Getter
    @Setter
    private int time;
    @Getter
    @Setter
    private int energy;

}