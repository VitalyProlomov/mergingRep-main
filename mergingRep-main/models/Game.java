package models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.*;

import static models.Action.ActionType.*;
import static models.PositionType.*;

/**
 * Class defining the entity of a game (1 hand played among players).
 * Contains information about cards, pot size, hands dealt to players
 * (if revealed) and other.
 */
public class Game {
    // Unique (At least for PokerOk)
    private final String gameId;
    private String table;

    @JsonIgnore
    public Game(String gameId, double bigBlindSize$, Collection<PlayerInGame> players) {
        this.gameId = gameId;
        this.bigBlindSize$ = bigBlindSize$;
        if (players != null) {
            for (PlayerInGame p : players) {
                this.players.put(p.getId(), p);
            }

            for (PlayerInGame p : players) {
                this.initialBalances.put(p.getId(), p.getBalance());
            }
        }

    }

    /**
     * Constructs a new game with given ID and BB (given in dollars)
     *
     * @param gameId        Id of the game from PokerCraft parsed text view of the game
     * @param bigBlindSize$ value of 1 big blind in dollars
     */
    @JsonCreator
    public Game(@JsonProperty("gameId") String gameId,
                @JsonProperty("bigBlindSize$") double bigBlindSize$,
                @JsonProperty("players") HashMap<String, PlayerInGame> players,
                @JsonProperty("initialBalances") HashMap<String, Double> initialBalances) {
        this.gameId = gameId;
        this.bigBlindSize$ = bigBlindSize$;
        if (players != null) {
            this.players.putAll(players);
        }
        if (initialBalances != null) {
            for (String id : initialBalances.keySet()) {
                this.initialBalances.put(id, initialBalances.get(id));
            }
        }
    }

    /**
     * Map containing pairs hash - player, that are present in given Game
     */
    @JsonProperty(value = "players", required = true)
    private HashMap<String, PlayerInGame> players = new HashMap<>();
    private final HashMap<String, Double> initialBalances = new HashMap<>();

    // Amount of dollars as a cash drop (or 0 if there is no cash drop)
    private double extraCashAmount = 0;
    private Date date;
    private final double bigBlindSize$;

    private StreetDescription preFlop;
    private StreetDescription flop;
    private StreetDescription turn;
    private StreetDescription river;

    @JsonProperty(value = "allWinners", required = true)
    private HashMap<String, Double> allWinners = new HashMap<>();
    private double finalPot;
    private double rake;

    /**
     * Contains all the single shown cards assigned to the players that showed them.
     */
    private final HashMap<String, Card> shownOneCards = new HashMap<>();

    /**
     * Adds information about one shown card (needed when player shows just one of his cards, not both).
     *
     * @param playerId id of hte player, that showed card.
     * @param card     card that was shown
     * @return true, if the player with given id is present in that game and information is added;
     * false, otherwise.
     */
    public boolean addShownOneCard(String playerId, Card card) {
        if (this.players.get(playerId) != null) {
            shownOneCards.put(playerId, card);
            return true;
        } else {
            return false;
        }
    }

    /**
     * @return hashMap that contains all the single shown cards assigned to players who showed them
     */
    public HashMap<String, Card> getShownOneCards() {
        return new HashMap<>(shownOneCards);
    }

    private int preFlopRaisesAmount = -1;

    /**
     * counts amount of preflop raises (EXcludes blind raises).
     */
    private void countPreFlopRaises() {
        StreetDescription pf = getPreFlop();
        if (pf == null) {
            return;
        }
        preFlopRaisesAmount = 0;
        for (int i = 0; i < pf.getAllActions().size(); ++i) {
            if (pf.getAllActions().get(i).getActionType().equals(RAISE)) {
                ++preFlopRaisesAmount;
            }
        }
    }

    @JsonIgnore
    public boolean isUnRaised() {
        if (preFlopRaisesAmount == -1) {
            countPreFlopRaises();
        }
        return preFlopRaisesAmount == 0;
    }

    /**
     * @return true, if the pot is single raised (only one raise took pace during preflop),
     * false otherwise
     */
    @JsonIgnore
    public boolean isSingleRaised() {
        if (preFlopRaisesAmount == -1) {
            countPreFlopRaises();
        }
        return preFlopRaisesAmount == 1;
    }

    /**
     * @return true, if the pot is 3 bet (2 raises took pace during preflop),
     * false otherwise
     */
    @JsonIgnore
    public boolean isPot3Bet() {
        if (preFlopRaisesAmount == -1) {
            countPreFlopRaises();
        }
        return preFlopRaisesAmount == 2;
    }

    /**
     * @return true, if the pot is 4 bet (3 raises took pace during preflop),
     * false otherwise
     */
    @JsonIgnore
    public boolean isPot4Bet() {
        if (preFlopRaisesAmount == -1) {
            countPreFlopRaises();
        }
        return preFlopRaisesAmount == 3;
    }

    /**
     * @return true, if the pot is 5+ bet (4 or more raises took pace during preflop),
     * false otherwise
     */
    @JsonIgnore
    public boolean isPot5PlusBet() {
        if (preFlopRaisesAmount == -1) {
            countPreFlopRaises();
        }
        return preFlopRaisesAmount >= 4;
    }

    /**
     * @return true if more than 2 players were active (didnt fold) after preflop,
     * false otherwise.
     */
    @JsonIgnore
    public boolean isPotMultiWay() {
        return preFlop.getPlayersAfterBetting().size() > 2;
    }

    /**
     * @return true if everybody but winner of the game folded on preflop,
     * false otherwise
     */
    @JsonIgnore
    public boolean isGameFoldedPreFlop() {
        return preFlop.getPlayersAfterBetting().size() == 1;
    }

    /**
     * @return true if Hero is in the game
     */
    @JsonIgnore
    public boolean isHeroInGame() {
        return getPlayer("Hero") != null;
    }

    @JsonIgnore
    public boolean isPlayerPFR(String hash) {
        if (players.get(hash) == null) {
            return false;
        }
        for (int i = preFlop.getAllActions().size() - 1; i >= 0; --i) {
            if (preFlop.getAllActions().get(i).getActionType().equals(RAISE)) {
                return preFlop.getAllActions().get(i).getPlayerId().equals(hash);
            }
        }
        return false;
    }

    @JsonIgnore
    public boolean is3BetRaiser(String hash) {
        if (players.get(hash) == null) {
            return false;
        }
        boolean was1RaiseFound = false;
        for (int i = 0; i < preFlop.getAllActions().size(); ++i) {
            if (preFlop.getAllActions().get(i).getActionType().equals(RAISE)) {
                if (was1RaiseFound) {
                    return preFlop.getAllActions().get(i).getPlayerId().equals(hash);
                }
                was1RaiseFound = true;
            }
        }
        return false;
    }

    @JsonIgnore
    public boolean is4BetRaiser(String hash) {
        if (players.get(hash) == null) {
            return false;
        }
        int raisesAmount = 0;
        for (int i = 0; i < preFlop.getAllActions().size(); ++i) {
            if (preFlop.getAllActions().get(i).getActionType().equals(RAISE)) {
                if (raisesAmount == 2) {
                    return preFlop.getAllActions().get(i).getPlayerId().equals(hash);
                }
                ++raisesAmount;
            }
        }
        return false;
    }

    @JsonIgnore
    public boolean is5BetRaiser(String hash) {
        if (players.get(hash) == null) {
            return false;
        }
        int raisesAmount = 0;
        for (int i = 0; i < preFlop.getAllActions().size(); ++i) {
            if (preFlop.getAllActions().get(i).getActionType().equals(RAISE)) {
                if (raisesAmount == 3) {
                    return preFlop.getAllActions().get(i).getPlayerId().equals(hash);
                }
                ++raisesAmount;
            }
        }
        return false;
    }

    @JsonIgnore
    public boolean didCBetFLop(String hash) {
        if (flop == null) {
            return false;
        }
        if (!isPlayerPFR(hash)) {
            return false;
        }
        int i = 0;
        while (i < flop.getAllActions().size() && flop.getAllActions().get(i).getActionType().equals(BET)) {
            ++i;
        }
        if (i >= flop.getAllActions().size()) {
            return false;
        }
        return flop.getAllActions().get(i).getPlayerId().equals(hash);
    }

    @JsonIgnore
    public boolean didCheckRaiseFlop(String hash) {
        if (flop == null) {
            return false;
        }
        boolean wasChecked = false;
        for (int i = 0; i < flop.getAllActions().size(); ++i) {
            if (flop.getAllActions().get(i).getPlayerId().equals(hash)) {
                if (wasChecked) {
                    return flop.getAllActions().get(i).getActionType().equals(RAISE);
                }
                wasChecked = true;
            }
        }
        return false;
    }

    @JsonIgnore
    public boolean didCallCBetFlop(String hash) {
        if (flop == null) {
            return false;
        }
        if (isPlayerPFR(hash)) {
            return false;
        }
        for (int i = 0; i < flop.getAllActions().size(); ++i) {
            if (flop.getAllActions().get(i).getPlayerId().equals(hash) &&
                    flop.getAllActions().get(i).getActionType().equals(CALL)) {
                return true;
            }
        }
        return false;
    }

    @JsonIgnore
    public boolean didRaiseFlop(String hash) {
        if (flop == null) {
            return false;
        }
        for (int i = 0; i < flop.getAllActions().size(); ++i) {
            if (flop.getAllActions().get(i).getPlayerId().equals(hash) &&
                    flop.getAllActions().get(i).getActionType().equals(RAISE)) {
                return true;
            }
        }
        return false;
    }

    @JsonIgnore
    public boolean didCBetTurn(String hash) {
        if (turn == null) {
            return false;
        }
        if (!didCBetFLop(hash)) {
            return false;
        }
        for (int i = 0; i < turn.getAllActions().size(); ++i) {
            if (turn.getAllActions().get(i).getActionType().equals(BET) &&
                    turn.getAllActions().get(i).getPlayerId().equals(hash)) {
                return true;
            }
        }
        return false;
    }

    @JsonIgnore
    public boolean didCheckRaiseTurn(String hash) {
        if (turn == null) {
            return false;
        }

        boolean wasChecked = false;
        for (int i = 0; i < turn.getAllActions().size(); ++i) {
            if (turn.getAllActions().get(i).getPlayerId().equals(hash)) {
                if (wasChecked) {
                    return turn.getAllActions().get(i).getActionType().equals(RAISE);
                }
                wasChecked = true;
            }
        }
        return false;
    }

    @JsonIgnore
    public boolean didCallCBetTurn(String hash) {
        if (turn == null) {
            return false;
        }
        if (isPlayerPFR(hash)) {
            return false;
        }
        for (int i = 0; i < turn.getAllActions().size(); ++i) {
            if (turn.getAllActions().get(i).getPlayerId().equals(hash) &&
                    turn.getAllActions().get(i).getActionType().equals(CALL)) {
                return true;
            }
        }
        return false;
    }

    @JsonIgnore
    public boolean didRaiseTurn(String hash) {
        if (turn == null) {
            return false;
        }
        for (int i = 0; i < turn.getAllActions().size(); ++i) {
            if (turn.getAllActions().get(i).getPlayerId().equals(hash) &&
                    turn.getAllActions().get(i).getActionType().equals(RAISE)) {
                return true;
            }
        }
        return false;
    }

    @JsonIgnore
    public boolean didCBetRiver(String hash) {
        if (river == null) {
            return false;
        }
        if (!didCBetTurn(hash)) {
            return false;
        }
        for (int i = 0; i < river.getAllActions().size(); ++i) {
            if (river.getAllActions().get(i).getActionType().equals(BET) &&
                    river.getAllActions().get(i).getPlayerId().equals(hash)) {
                return true;
            }
        }
        return false;
    }

    @JsonIgnore
    public boolean didLeadRiver(String hash) {
        if (river == null) {
            return false;
        }
        if (didCBetTurn(hash)) {
            return false;
        }
        for (int i = 0; i < river.getAllActions().size(); ++i) {
            if (river.getAllActions().get(i).getActionType().equals(BET) &&
                    river.getAllActions().get(i).getPlayerId().equals(hash)) {
                return true;
            }
        }
        return false;
    }

    @JsonIgnore
    public boolean didWinAtShowdownRiver(String hash) {
        if (river == null) {
            return false;
        }
        if (river.getPlayersAfterBetting().contains(new PlayerInGame(hash))) {
            return (allWinners.containsKey(hash));
        }
        return false;
    }

    @JsonIgnore
    public boolean didCallRiver(String hash) {
        if (river == null) {
            return false;
        }
        for (int i = 0; i < river.getAllActions().size(); ++i) {
            if (river.getAllActions().get(i).getPlayerId().equals(hash)) {
                return river.getAllActions().get(i).getActionType().equals(CALL);
            }
        }
        return false;
    }


    /**
     * @return if Hero did NOT fold on preflop,
     * false otherwise
     */
    @JsonIgnore
    public boolean isHeroPostFlop() {
        return preFlop.getPlayersAfterBetting().contains(new PlayerInGame("Hero"));
    }

    @JsonIgnore
    public double getHeroWinloss() {
        if (players.get("Hero") == null) {
            return 0;
        }
        return players.get("Hero").getBalance() - initialBalances.get("Hero");
    }

    /**
     * @return ID of the game
     */
    public String getGameId() {
        return gameId;
    }

    /**
     * @return Big Blind (BB) size in dollars.
     */
    public double getBigBlindSize$() {
        return bigBlindSize$;
    }

    /**
     * @return Date when the game took place.
     */
    public Date getDate() {
        return date;
    }

    /**
     * sets the date of the game
     *
     * @param date given date to set (no setting conditions)
     */
    public void setDate(Date date) {
        this.date = date;
    }

    /**
     * @return a HashSet of the players in game (copy, not a link)
     */
    public HashMap<String, PlayerInGame> getPlayers() {
        if (players == null) {
            return null;
        }
        return new HashMap<>(players);
    }

    /**
     * @return a HashMap of the players in game (copy, not a link)
     */
    public HashMap<PositionType, PlayerInGame> getPosPlayersMap() {
        if (players == null) {
            return null;
        }
        HashMap<PositionType, PlayerInGame> posP = new HashMap<>();
        for (PlayerInGame p : players.values()) {
            posP.put(p.getPosition(), p);
        }
        return posP;
    }

    /**
     * Returns the copy player in the game with the corresponding hash. If there is
     * no player in game with such hash, null is returned
     *
     * @param id id of the PlayerInGame to get
     * @return player in game with same hash. Or null if no such player is found
     */
    public PlayerInGame getPlayer(String id) {
        return new PlayerInGame(players.get(id));
    }

    /**
     * Sets the hand of the player on given position to the given hand
     *
     * @param pos  position of the player
     * @param hand Hand to set
     * @return true if player with such id was found and the hand was added. False, otherwise
     */
    public boolean setPlayerHand(PositionType pos, Hand hand) {
        for (PlayerInGame p : players.values()) {
            if (pos == p.getPosition()) {
                p.setHand(hand);
                return true;
            }
        }
        return false;
    }


    /**
     * Sets the hand of the player with given id to the given hand.
     *
     * @param id   id of the player
     * @param hand Hand to set
     * @return true if player with such id was found and the hand was added. False, otherwise
     */
    public boolean setPlayerHand(String id, Hand hand) {
        PlayerInGame p = players.get(id);
        if (p == null) {
            return false;
        }
        p.setHand(hand);
        return true;
    }

    /**
     * Sets Hero`s hand to the given hand
     *
     * @param hand hand to set
     * @return true if player with such id was found and the hand was added. False, otherwise
     */
    public boolean setHeroHand(Hand hand) {
        return setPlayerHand("Hero", hand);
    }

//    /**
//     * Sets players and updates the initial balances (inside the Game)
//     *
//     * @param players players to set
//     */
//    public void setPlayers(Set<PlayerInGame> players) {
//        // Should think about working w nulls.
//        this.players = new HashSet<>();
//        this.players.addAll(players);
//
//        HashMap<String, Double> initB = new HashMap<>();
//        for (PlayerInGame p : players) {
//            initialBalances.put(p.getId(), p.getBalance());
//        }
//        setInitialBalances(initB);
//    }

//    /**
//     * Sets players and updates the initial balances (inside the Game)
//     *
//     * @param players players to set
//     */
//    public void setPlayers(Set<PlayerInGame> players) {
//        this.players = new HashMap<>();
//        for (PlayerInGame p : players) {
//            this.players.put(p.getPosition(), p);
//        }
//        setInitialBalances(new ArrayList<>(players));
//    }

    /**
     * Subtracts given amount from the balance of the player with given Id.
     *
     * @param id         Id of player whose balance is needed to be decreased
     * @param decrAmount amount that will be decreased from given player`s balance
     * @throws IllegalArgumentException if {@code decrAmount} is less than players balance or decrAmount is less than 0
     */
    public void decrementPlayersBalance(String id, double decrAmount) {
        if (decrAmount < 0) {
            throw new IllegalArgumentException("Decrement amount must be positive (ypu can not add chips to player`s balance during hand");
        }

        PlayerInGame p = this. players.get(id);
        if (p != null) {
            double balance = p.getBalance();

            if (decrAmount - balance > 0.01) {
                throw new IllegalArgumentException("Decrement amount must be less or equal to the balance of the player");
            }

            p.setBalance(p.getBalance() - decrAmount);
        }
    }

    /**
     * Returns the given amount to the player`s balance (if bet was uncalled)
     *
     * @param playerId     id of the player to return chips
     * @param returnAmount amount to return to player`s balance
     */
    public void returnUncalledChips(String playerId, double returnAmount) {
        PlayerInGame p = this.players.get(playerId);
        if (p != null) {
            p.setBalance(p.getBalance() + returnAmount);
        }
    }

    /**
     * @return ture if extra cash is more than zero,
     * false otherwise.
     */
    @JsonIgnore
    public boolean isExtraCash() {
        return extraCashAmount != 0;
    }

    /**
     * Sets extra cash (can not be less than zero)
     *
     * @param amount amount ot set
     * @throws IllegalArgumentException if amount is less than zero
     */
    public void setExtraCash(double amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be >= 0");
        }

        extraCashAmount = amount;
    }

    /**
     * @return extra cash amount
     */
    public double getExtraCashAmount() {
        return extraCashAmount;
    }

    /**
     * Calculates the small blind size (as for the 29.03.2023, all the sizes on
     * PokerOk, if BB size is even, SB = 0.5 BB, if BB is an odd amount, then
     * SB = 0.4 BB).
     *
     * @return Small blind amount in dollars
     */
    @JsonIgnore
    public double getSB() {
        if (bigBlindSize$ * 100 % 2 == 0) {
            return bigBlindSize$ / 2;
        }
        return bigBlindSize$ * 0.4;
    }

    /**
     * @return copy of the preflop StreetDescription
     */
    public StreetDescription getPreFlop() {
        if (this.preFlop == null) {
            return null;
        }
        return new StreetDescription(preFlop);
    }

    /**
     * Sets preflop (sets a copy of given StreetDescription, not a link)
     *
     * @param preFlop given StreetDescription
     */
    public void setPreFlop(StreetDescription preFlop) {
        if (preFlop == null) {
            this.preFlop = null;
            return;
        }
        this.preFlop = new StreetDescription(preFlop);
    }

    /**
     * @return copy of the flop StreetDescription
     */
    public StreetDescription getFlop() {
        if (flop == null) {
            return null;
        }
        return new StreetDescription(flop);
    }


    /**
     * Sets flop (sets a copy of given StreetDescription, not a link)
     *
     * @param flop given StreetDescription
     */
    public void setFlop(StreetDescription flop) {
        if (flop == null) {
            this.flop = null;
            return;
        }
        this.flop = new StreetDescription(flop);
    }

    /**
     * @return copy of the turn StreetDescription
     */
    public StreetDescription getTurn() {
        if (turn == null) {
            return null;
        }
        return new StreetDescription(turn);
    }


    /**
     * Sets turn (sets a copy of given StreetDescription, not a link)
     *
     * @param turn given StreetDescription
     */
    public void setTurn(StreetDescription turn) {
        if (turn == null) {
            this.turn = null;
            return;
        }
        this.turn = new StreetDescription(turn);
    }

    /**
     * @return copy of the river StreetDescription
     */
    public StreetDescription getRiver() {
        if (this.river == null) {
            return null;
        }
        return new StreetDescription(river);
    }


    /**
     * Sets river (sets a copy of given StreetDescription, not a link)
     *
     * @param river given StreetDescription
     */
    public void setRiver(StreetDescription river) {
        if (river == null) {
            this.river = null;
            return;
        }
        this.river = new StreetDescription(river);
    }

    /**
     * @return the HashMap of winners in this game, containing id`s and
     * amount won assigned to the player with given id
     */
    public HashMap<String, Double> getWinners() {
        if (allWinners == null) {
            return null;
        }
        return new HashMap<String, Double>(allWinners);
    }

    public void setWinners(HashMap<String, Double> map) {
        if (map == null) {
            this.allWinners = null;
            return;
        }
        for (String key : map.keySet()) {
            addWinner(key, map.get(key));
        }
    }

    /**
     * Adds a winner and assigns amount on to the winner hash set.
     */
    public void addWinner(String winnerId, double amount) {
        this.allWinners = new HashMap<>();
        allWinners.put(winnerId, amount);
        PlayerInGame p = players.get(winnerId);
        if (p != null) {
            p.setBalance(p.getBalance() + amount);
        }
    }

    /**
     * @return final pot of the game
     */
    public double getFinalPot() {
        if (river != null) {
            int len = river.getAllActions().size();
            finalPot = river.getAllActions().get(len - 1).getPotBeforeAction() + river.getAllActions().get(len - 1).getAmount();
            return finalPot;
        } else if (turn != null) {
            int len = turn.getAllActions().size();
            finalPot = turn.getAllActions().get(len - 1).getPotBeforeAction() + turn.getAllActions().get(len - 1).getAmount();
            return finalPot;
        } else if (flop != null) {
            int len = flop.getAllActions().size();
            finalPot = flop.getAllActions().get(len - 1).getPotBeforeAction() + flop.getAllActions().get(len - 1).getAmount();
            return finalPot;
        } else if (preFlop != null) {
            int len = preFlop.getAllActions().size();
            finalPot = preFlop.getAllActions().get(len - 1).getPotBeforeAction() + preFlop.getAllActions().get(len - 1).getAmount();
            return finalPot;
        }
        return 0;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public String getTable() {
        return table;
    }

    /**
     * @return HashMap of all the initial balances of players in game
     */
    public HashMap<String, Double> getInitialBalances() {
        return new HashMap<>(initialBalances);
    }

    /**
     * Sets initial balances of the players
     *
     * @param players hashMap of balances
     */
    private void setInitialBalances(Map<String, Double> players) {
        initialBalances.putAll(players);
    }

    /**
     * @return the rake
     */
    public double getRake() {
        return rake;
    }

    /**
     * Sets the rake
     *
     * @param rake rake to set
     */
    public void setRake(double rake) {
        this.rake = rake;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj.getClass() != Game.class) {
            return false;
        }
        return this.gameId.equals(((Game) obj).gameId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(gameId);
    }

    /**
     * @return string representation of the game, that contains id of the game, list of players
     * and all StreetsDescriptions
     */
    @Override
    public String toString() {
        ArrayList<PlayerInGame> orderedPlayers = new ArrayList<>();
        ArrayList<PositionType> orderPos = new ArrayList<>(List.of(SB, BB, TB, UTG, UTG_1, UTG_2, LJ, HJ, CO, BTN));
        for (PositionType orderPo : orderPos) {
            PlayerInGame p = null;
            for (PlayerInGame iterP : players.values()) {
                if (iterP.getPosition() == orderPo) {
                    p = iterP;
                }
            }

            if (p != null)
                orderedPlayers.add(new PlayerInGame(p));
        }

        for (PlayerInGame p : orderedPlayers) {
            p.setBalance(this.initialBalances.get(p.getId()));
        }

        return "(Game| Game Id: " + gameId + ",\nPlayers: " + orderedPlayers +
                ",\nPreflop: " + preFlop + ",\nFlop: " + flop + ",\nTurn: " + turn + ",\nRiver: " + river + ")";
    }
}