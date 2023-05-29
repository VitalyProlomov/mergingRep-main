package appinterface.controllers;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import models.Game;
import org.controlsfx.control.CheckComboBox;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

public class FilterSearchController {
    @FXML
    private CheckComboBox<Double> bbSizeCheckComboBox;

    @FXML
    private Button clearButton;

    @FXML
    private CheckBox unraisedCheckBox;
    @FXML
    private CheckBox fiveBetCheckBox;

    @FXML
    private CheckBox fourBetCheckBox;

    @FXML
    private DatePicker fromDatePicker;

    @FXML
    private CheckComboBox<String> gameTypeCheckComboBox;

    @FXML
    private CheckComboBox<String> playersPostFlopCheckComboBox;

    @FXML
    private CheckComboBox<String> roomCheckComboBox;

    @FXML
    private Button searchButton;

    @FXML
    private CheckBox sprCheckBox;

    @FXML
    private CheckBox threeBetCheckBox;

    @FXML
    private DatePicker toDatePicker;

    private HashMap<String, Game> unfilteredGames;
    private HashMap<String, Game> gamesAfterFilter;

    public void setUnfilteredGames(Map<String, Game> games) {
        this.unfilteredGames = new HashMap<>(games);
    }

    public Map<String, Game> getGamesAfterFilter() {
        return gamesAfterFilter;
    }

    private void setToDefault() {
        bbSizeCheckComboBox.getCheckModel().clearChecks();
        playersPostFlopCheckComboBox.getCheckModel().clearChecks();
        roomCheckComboBox.getCheckModel().clearChecks();
        gameTypeCheckComboBox.getCheckModel().clearChecks();
        sprCheckBox.setSelected(true);
        threeBetCheckBox.setSelected(true);
        fourBetCheckBox.setSelected(true);
        fourBetCheckBox.setSelected(true);
        fiveBetCheckBox.setSelected(true);

        fromDatePicker.setValue(null);
        toDatePicker.setValue(null);
    }

    @FXML
    public void initialize() {
        bbSizeCheckComboBox.getItems().addAll(FXCollections.observableArrayList(0.02, 0.05, 0.10, 0.25));
        playersPostFlopCheckComboBox.getItems().addAll(FXCollections.observableArrayList("Heads-up", "Multi-way", "Folded"));
        roomCheckComboBox.getItems().addAll(FXCollections.observableArrayList("GGPokerok"));
        gameTypeCheckComboBox.getItems().addAll(FXCollections.observableArrayList("Rush`n`Cash", "8 max 3 Blinds Holdem"));

        searchButton.setOnMouseClicked(action -> {
            gamesAfterFilter = (HashMap<String, Game>) searchFilteredGames(unfilteredGames);
            this.searchButton.getScene().getWindow().hide();
        });
        clearButton.setOnMouseClicked(action -> {
            setToDefault();
        });
    }

    public Map<String, Game> searchFilteredGames(Map<String, Game> unfilteredGames) {
        if (unfilteredGames == null) {
            return new HashMap<>();
        }
        if (unfilteredGames.size() == 0) {
            return unfilteredGames;
        }

        HashSet<Double> chosenBBSizes =
                new HashSet<>(bbSizeCheckComboBox.getCheckModel().getCheckedItems().stream().toList());

        HashMap<String, Game> filteredGames = new HashMap<>(unfilteredGames);

        LocalDate localDate = fromDatePicker.getValue();
        Date filteredFromDate;
        if (localDate == null) {
            filteredFromDate = null;
        } else {
            Instant instant = Instant.from(localDate.atStartOfDay(ZoneId.systemDefault()));
            filteredFromDate = Date.from(instant);
        }

        localDate = toDatePicker.getValue();
        Date filteredToDate;
        if (localDate == null) {
            filteredToDate = null;
        } else {
            Instant instant = Instant.from(localDate.atStartOfDay(ZoneId.systemDefault()));
            filteredToDate = Date.from(instant);
        }

        boolean headsUpFilter = playersPostFlopCheckComboBox.getCheckModel().isChecked(0);
        boolean multiWayFilter = playersPostFlopCheckComboBox.getCheckModel().isChecked(1);
        boolean foldedFilter = playersPostFlopCheckComboBox.getCheckModel().isChecked(2);
        if (!headsUpFilter && !multiWayFilter && !foldedFilter) {
            headsUpFilter = true;
            multiWayFilter = true;
            foldedFilter = true;
        }

        HashSet<String> chosenGamesTypes =
                new HashSet<>(gameTypeCheckComboBox.getCheckModel().getCheckedItems().stream().toList());
        boolean rncChosen = chosenGamesTypes.contains("Rush`n`Cash");
        boolean nl8MaxChosen3Blinds = chosenGamesTypes.contains("8 max 3 Blinds Holdem");
        if (!rncChosen && !nl8MaxChosen3Blinds) {
            rncChosen = true;
            nl8MaxChosen3Blinds = true;
        }

        boolean isUnraisedChosen = unraisedCheckBox.isSelected();
        boolean isSPRChosen = sprCheckBox.isSelected();
        boolean is3BetChosen = threeBetCheckBox.isSelected();
        boolean is4BetChosen = fourBetCheckBox.isSelected();
        boolean is5BetChosen = fiveBetCheckBox.isSelected();

        for (String id : unfilteredGames.keySet()) {
            Game g = unfilteredGames.get(id);
            if (chosenBBSizes.size() != 0 && !chosenBBSizes.contains(g.getBigBlindSize$())) {
                filteredGames.remove(id);
                continue;
            }

            if (filteredFromDate != null && filteredFromDate.after(g.getDate())) {
                filteredGames.remove(id);
                continue;
            }

            if (filteredToDate != null && filteredToDate.before(g.getDate())) {
                filteredGames.remove(id);
                continue;
            }

            if (g.getPreFlop().getPlayersAfterBetting().size() > 2 && !multiWayFilter ||
                    g.getPreFlop().getPlayersAfterBetting().size() == 2 && !headsUpFilter ||
                    g.getPreFlop().getPlayersAfterBetting().size() == 1 && !foldedFilter) {
                filteredGames.remove(id);
                continue;
            }

            if (!rncChosen && g.getGameId().startsWith("RC")) {
                filteredGames.remove(id);
                continue;
            }
            if (!nl8MaxChosen3Blinds &&
                    g.getGameId().startsWith("HD")) {
                filteredGames.remove(id);
                continue;
            }

            if (g.isUnRaised() && !isUnraisedChosen ||
                    g.isSingleRaised() && !isSPRChosen ||
                    g.isPot3Bet() && !is3BetChosen ||
                    g.isPot4Bet() && !is4BetChosen ||
                    g.isPot5PlusBet() && !is5BetChosen) {
                filteredGames.remove(id);
            }
        }
        return filteredGames;
    }
}
