package models;

@Data
@ToString
@AllArgsConstructor
public class Ticker {
    private double high;
    private double low;
    private double bid;
    private double ask;
    private double last;
    private double volume;
    private double baseVolume;
    private long timestamp;
}