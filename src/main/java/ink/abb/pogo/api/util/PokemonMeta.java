package ink.abb.pogo.api.util;

import POGOProtos.Enums.PokemonFamilyIdOuterClass.PokemonFamilyId;
import POGOProtos.Enums.PokemonIdOuterClass.PokemonId;
import POGOProtos.Enums.PokemonMoveOuterClass.PokemonMove;

public class PokemonMeta {
    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public PokemonFamilyId getFamily() {
        return family;
    }

    public void setFamily(PokemonFamilyId family) {
        this.family = family;
    }

    public PokemonClass getPokemonClass() {
        return pokemonClass;
    }

    public void setPokemonClass(PokemonClass pokemonClass) {
        this.pokemonClass = pokemonClass;
    }

    public PokemonType getType2() {
        return type2;
    }

    public void setType2(PokemonType type2) {
        this.type2 = type2;
    }

    public double getPokedexHeightM() {
        return pokedexHeightM;
    }

    public void setPokedexHeightM(double pokedexHeightM) {
        this.pokedexHeightM = pokedexHeightM;
    }

    public double getHeightStdDev() {
        return heightStdDev;
    }

    public void setHeightStdDev(double heightStdDev) {
        this.heightStdDev = heightStdDev;
    }

    public int getBaseStamina() {
        return baseStamina;
    }

    public void setBaseStamina(int baseStamina) {
        this.baseStamina = baseStamina;
    }

    public double getCylRadiusM() {
        return cylRadiusM;
    }

    public void setCylRadiusM(double cylRadiusM) {
        this.cylRadiusM = cylRadiusM;
    }

    public double getBaseFleeRate() {
        return baseFleeRate;
    }

    public void setBaseFleeRate(double baseFleeRate) {
        this.baseFleeRate = baseFleeRate;
    }

    public int getBaseAttack() {
        return baseAttack;
    }

    public void setBaseAttack(int baseAttack) {
        this.baseAttack = baseAttack;
    }

    public double getDiskRadiusM() {
        return diskRadiusM;
    }

    public void setDiskRadiusM(double diskRadiusM) {
        this.diskRadiusM = diskRadiusM;
    }

    public double getCollisionRadiusM() {
        return collisionRadiusM;
    }

    public void setCollisionRadiusM(double collisionRadiusM) {
        this.collisionRadiusM = collisionRadiusM;
    }

    public double getPokedexWeightKg() {
        return pokedexWeightKg;
    }

    public void setPokedexWeightKg(double pokedexWeightKg) {
        this.pokedexWeightKg = pokedexWeightKg;
    }

    public MovementType getMovementType() {
        return movementType;
    }

    public void setMovementType(MovementType movementType) {
        this.movementType = movementType;
    }

    public PokemonType getType1() {
        return type1;
    }

    public void setType1(PokemonType type1) {
        this.type1 = type1;
    }

    public double getCollisionHeadRadiusM() {
        return collisionHeadRadiusM;
    }

    public void setCollisionHeadRadiusM(double collisionHeadRadiusM) {
        this.collisionHeadRadiusM = collisionHeadRadiusM;
    }

    public double getMovementTimerS() {
        return movementTimerS;
    }

    public void setMovementTimerS(double movementTimerS) {
        this.movementTimerS = movementTimerS;
    }

    public double getJumpTimeS() {
        return jumpTimeS;
    }

    public void setJumpTimeS(double jumpTimeS) {
        this.jumpTimeS = jumpTimeS;
    }

    public double getModelScale() {
        return modelScale;
    }

    public void setModelScale(double modelScale) {
        this.modelScale = modelScale;
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    public int getBaseDefense() {
        return baseDefense;
    }

    public void setBaseDefense(int baseDefense) {
        this.baseDefense = baseDefense;
    }

    public int getAttackTimerS() {
        return attackTimerS;
    }

    public void setAttackTimerS(int attackTimerS) {
        this.attackTimerS = attackTimerS;
    }

    public double getWeightStdDev() {
        return weightStdDev;
    }

    public void setWeightStdDev(double weightStdDev) {
        this.weightStdDev = weightStdDev;
    }

    public double getCylHeightM() {
        return cylHeightM;
    }

    public void setCylHeightM(double cylHeightM) {
        this.cylHeightM = cylHeightM;
    }

    public int getCandyToEvolve() {
        return candyToEvolve;
    }

    public void setCandyToEvolve(int candyToEvolve) {
        this.candyToEvolve = candyToEvolve;
    }

    public double getCollisionHeightM() {
        return collisionHeightM;
    }

    public void setCollisionHeightM(double collisionHeightM) {
        this.collisionHeightM = collisionHeightM;
    }

    public double getShoulderModeScale() {
        return shoulderModeScale;
    }

    public void setShoulderModeScale(double shoulderModeScale) {
        this.shoulderModeScale = shoulderModeScale;
    }

    public double getBaseCaptureRate() {
        return baseCaptureRate;
    }

    public void setBaseCaptureRate(double baseCaptureRate) {
        this.baseCaptureRate = baseCaptureRate;
    }

    public PokemonId getParentId() {
        return parentId;
    }

    public void setParentId(PokemonId parentId) {
        this.parentId = parentId;
    }

    public double getCylGroundM() {
        return cylGroundM;
    }

    public void setCylGroundM(double cylGroundM) {
        this.cylGroundM = cylGroundM;
    }

    public PokemonMove[] getQuickMoves() {
        return quickMoves;
    }

    public void setQuickMoves(PokemonMove[] quickMoves) {
        this.quickMoves = quickMoves;
    }

    public PokemonMove[] getCinematicMoves() {
        return cinematicMoves;
    }

    public void setCinematicMoves(PokemonMove[] cinematicMoves) {
        this.cinematicMoves = cinematicMoves;
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    private String templateId;


    private PokemonFamilyId family;


    private PokemonClass pokemonClass;


    private PokemonType type2;


    private double pokedexHeightM;


    private double heightStdDev;


    private int baseStamina;


    private double cylRadiusM;


    private double baseFleeRate;


    private int baseAttack;


    private double diskRadiusM;


    private double collisionRadiusM;


    private double pokedexWeightKg;


    private MovementType movementType;


    private PokemonType type1;


    private double collisionHeadRadiusM;


    private double movementTimerS;


    private double jumpTimeS;


    private double modelScale;


    private String uniqueId;


    private int baseDefense;


    private int attackTimerS;


    private double weightStdDev;


    private double cylHeightM;


    private int candyToEvolve;


    private double collisionHeightM;


    private double shoulderModeScale;


    private double baseCaptureRate;


    private PokemonId parentId;


    private double cylGroundM;


    private PokemonMove[] quickMoves;


    private PokemonMove[] cinematicMoves;


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