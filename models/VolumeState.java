package models;

import coinbot.modules.bots.services.ThreeAccountBotServce;

import java.util.ArrayList;
import java.util.List;

@Data
public class VolumeState extends State {
    long stateCreationTimestamp;
    double acceleratedAmount, acceleratedBaseAmount;
    List<Double> accountsDepth = new ArrayList<>();
    long lastPerformedVolumeAcceleration;
    ThreeAccountBotServce.PerformAccount lastAccounts;
    int countPerformedAlienOrders;

    private VolumeState() {
    }
}