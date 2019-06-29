public class State{
    @Override
    public void resetState(){
        synchronized (thread.getWorkingState()) {
            thread.resetWorkingState();
        }
    }

    @Override
    protected WorkingThread loadState() {
        return settings.getExchange().getState(settings, this::log);
    }

    @Override
    protected void saveState() {

    }

    @Override
    public String getState() {
        return thread.getWorkingState().toString();
    }

    private String statePath(){
        return ServerResources.statesPrefix+ "/"+id+"/"+".data";
    }
}