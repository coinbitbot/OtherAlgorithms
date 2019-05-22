package models;

@Data
public abstract class State{
    abstract public String serialize();
    abstract protected void reset(long userId, String pair, Exchange exchange);
}