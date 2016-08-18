package ink.abb.pogo.api.util;

import POGOProtos.Enums.PokemonFamilyIdOuterClass.PokemonFamilyId;
import POGOProtos.Enums.PokemonIdOuterClass.PokemonId;
import POGOProtos.Enums.PokemonMoveOuterClass.PokemonMove;
import lombok.Getter;
import lombok.Setter;

public class PokemonMeta {
    @Getter
    @Setter
    private String templateId;
    @Getter
    @Setter
    private PokemonFamilyId family;
    @Getter
    @Setter
    private PokemonClass pokemonClass;
    @Getter
    @Setter
    private PokemonType type2;
    @Getter
    @Setter
    private double pokedexHeightM;
    @Getter
    @Setter
    private double heightStdDev;
    @Getter
    @Setter
    private int baseStamina;
    @Getter
    @Setter
    private double cylRadiusM;
    @Getter
    @Setter
    private double baseFleeRate;
    @Getter
    @Setter
    private int baseAttack;
    @Getter
    @Setter
    private double diskRadiusM;
    @Getter
    @Setter
    private double collisionRadiusM;
    @Getter
    @Setter
    private double pokedexWeightKg;
    @Getter
    @Setter
    private MovementType movementType;
    @Getter
    @Setter
    private PokemonType type1;
    @Getter
    @Setter
    private double collisionHeadRadiusM;
    @Getter
    @Setter
    private double movementTimerS;
    @Getter
    @Setter
    private double jumpTimeS;
    @Getter
    @Setter
    private double modelScale;
    @Getter
    @Setter
    private String uniqueId;
    @Getter
    @Setter
    private int baseDefense;
    @Getter
    @Setter
    private int attackTimerS;
    @Getter
    @Setter
    private double weightStdDev;
    @Getter
    @Setter
    private double cylHeightM;
    @Getter
    @Setter
    private int candyToEvolve;
    @Getter
    @Setter
    private double collisionHeightM;
    @Getter
    @Setter
    private double shoulderModeScale;
    @Getter
    @Setter
    private double baseCaptureRate;
    @Getter
    @Setter
    private PokemonId parentId;
    @Getter
    @Setter
    private double cylGroundM;
    @Getter
    @Setter
    private PokemonMove[] quickMoves;
    @Getter
    @Setter
    private PokemonMove[] cinematicMoves;
    @Getter
    @Setter
    private int number;

    public enum PokemonClass {
        NONE,
        VERY_COMMON,
        COMMON,
        UNCOMMON,
        RARE,
        VERY_RARE,
        EPIC,
        LEGENDARY,
        MYTHIC;
    }

    public enum PokemonType {
        NONE,
        GRASS,
        FIRE,
        WATER,
        BUG,
        ELECTRIC,
        POISON,
        FAIRY,
        NORMAL,
        PSYCHIC,
        FIGHTING,
        DRAGON,
        FLYING,
        ICE,
        ROCK,
        GROUND,
        GHOST,
        STEEL,
        DARK;
    }
    public enum MovementType {
        PSYCHIC,
        FLYING,
        ELETRIC,
        NORMAL,
        HOVERING,
        JUMP, ELECTRIC;
    }
}